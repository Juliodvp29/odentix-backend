package com.julio.odentix.odentix_backend.crm.service;

import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.crm.dto.CreateLeadActivityRequest;
import com.julio.odentix.odentix_backend.crm.dto.CreateLeadRequest;
import com.julio.odentix.odentix_backend.crm.dto.LeadActivityResponse;
import com.julio.odentix.odentix_backend.crm.dto.LeadResponse;
import com.julio.odentix.odentix_backend.crm.dto.UpdateLeadRequest;
import com.julio.odentix.odentix_backend.crm.dto.UpdateLeadStatusRequest;
import com.julio.odentix.odentix_backend.crm.entity.Lead;
import com.julio.odentix.odentix_backend.crm.entity.LeadActivity;
import com.julio.odentix.odentix_backend.crm.entity.LeadActivityType;
import com.julio.odentix.odentix_backend.crm.entity.LeadStatus;
import com.julio.odentix.odentix_backend.crm.repository.LeadActivityRepository;
import com.julio.odentix.odentix_backend.crm.repository.LeadRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para la gestión de prospectos comerciales y su embudo en el CRM (FASE5-02).
 */
@Service
public class LeadService {

  private final LeadRepository leadRepository;
  private final LeadActivityRepository leadActivityRepository;
  private final UserRepository userRepository;

  public LeadService(
      LeadRepository leadRepository,
      LeadActivityRepository leadActivityRepository,
      UserRepository userRepository) {
    this.leadRepository = leadRepository;
    this.leadActivityRepository = leadActivityRepository;
    this.userRepository = userRepository;
  }

  /**
   * Crea un nuevo prospecto/lead en el CRM.
   */
  @Transactional
  public LeadResponse createLead(CreateLeadRequest request) {
    UUID tenantId = TenantContext.getTenantId();

    User assignedTo = null;
    if (request.getAssignedToId() != null) {
      assignedTo = findUserInTenant(request.getAssignedToId(), tenantId);
    }

    Lead lead = new Lead(
        tenantId,
        request.getFullName().trim(),
        request.getPhone() != null ? request.getPhone().trim() : null,
        request.getEmail() != null ? request.getEmail().trim() : null,
        request.getSource() != null ? request.getSource().trim() : null
    );
    lead.setCampaign(request.getCampaign());
    lead.setProcedureOfInterest(request.getProcedureOfInterest());
    lead.setEstimatedValueCop(request.getEstimatedValueCop());
    lead.setAssignedTo(assignedTo);
    lead.setNextActionAt(request.getNextActionAt());

    Lead saved = leadRepository.save(lead);
    return LeadResponse.fromEntity(saved);
  }

  /**
   * Consulta el detalle de un lead por su identificador.
   */
  @Transactional(readOnly = true)
  public LeadResponse getLeadById(UUID id) {
    UUID tenantId = TenantContext.getTenantId();
    Lead lead = leadRepository.findWithDetailsByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado con id: " + id));
    return LeadResponse.fromEntity(lead);
  }

  /**
   * Lista prospectos comerciales con paginación y filtros opcionales.
   */
  @Transactional(readOnly = true)
  public Page<LeadResponse> listLeads(LeadStatus status, UUID assignedToId, String source, Pageable pageable) {
    UUID tenantId = TenantContext.getTenantId();
    String cleanSource = (source != null && !source.isBlank()) ? source.trim().toLowerCase() : null;

    Specification<Lead> spec = (root, query, cb) -> {
      List<Predicate> predicates = new ArrayList<>();
      predicates.add(cb.equal(root.get("tenantId"), tenantId));

      if (status != null) {
        predicates.add(cb.equal(root.get("status"), status));
      }
      if (assignedToId != null) {
        predicates.add(cb.equal(root.get("assignedTo").get("id"), assignedToId));
      }
      if (cleanSource != null) {
        predicates.add(cb.equal(cb.lower(root.get("source")), cleanSource));
      }

      if (query != null && query.getResultType() != Long.class && query.getResultType() != long.class) {
        root.fetch("assignedTo", JoinType.LEFT);
        root.fetch("convertedPatient", JoinType.LEFT);
      }

      return cb.and(predicates.toArray(new Predicate[0]));
    };

    return leadRepository.findAll(spec, pageable).map(LeadResponse::fromEntity);
  }

  /**
   * Actualiza la información comercial y demográfica de un lead.
   */
  @Transactional
  public LeadResponse updateLead(UUID id, UpdateLeadRequest request) {
    UUID tenantId = TenantContext.getTenantId();
    Lead lead = leadRepository.findWithDetailsByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado con id: " + id));

    User assignedTo = null;
    if (request.getAssignedToId() != null) {
      assignedTo = findUserInTenant(request.getAssignedToId(), tenantId);
    }

    lead.setFullName(request.getFullName().trim());
    lead.setPhone(request.getPhone() != null ? request.getPhone().trim() : null);
    lead.setEmail(request.getEmail() != null ? request.getEmail().trim() : null);
    lead.setSource(request.getSource() != null ? request.getSource().trim() : null);
    lead.setCampaign(request.getCampaign());
    lead.setProcedureOfInterest(request.getProcedureOfInterest());
    lead.setEstimatedValueCop(request.getEstimatedValueCop());
    lead.setAssignedTo(assignedTo);
    lead.setNextActionAt(request.getNextActionAt());

    Lead saved = leadRepository.save(lead);
    return LeadResponse.fromEntity(saved);
  }

  /**
   * Cambia el estado del lead en el pipeline de ventas.
   *
   * <p>Permite avances y retrocesos flexibles conforme al requerimiento de FASE5-02.
   * Si se proporcionan notas explicativas, se registra automáticamente una actividad de tipo 'nota'.
   */
  @Transactional
  public LeadResponse updateLeadStatus(UUID id, UpdateLeadStatusRequest request, UUID currentUserId) {
    UUID tenantId = TenantContext.getTenantId();
    Lead lead = leadRepository.findWithDetailsByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado con id: " + id));

    LeadStatus oldStatus = lead.getStatus();
    lead.setStatus(request.getStatus());

    if (request.getNotes() != null && !request.getNotes().isBlank()) {
      User currentUser = (currentUserId != null) ? userRepository.findById(currentUserId).orElse(null) : null;
      String nota = "Transición de " + oldStatus + " a " + request.getStatus() + ": " + request.getNotes().trim();
      LeadActivity activity = new LeadActivity(tenantId, lead, currentUser, LeadActivityType.nota, nota);
      leadActivityRepository.save(activity);
    }

    Lead saved = leadRepository.save(lead);
    return LeadResponse.fromEntity(saved);
  }

  /**
   * Registra una nueva interacción o actividad de contacto con el prospecto.
   *
   * <p>Si la actividad es de contacto directo ('llamada', 'whatsapp', 'email'),
   * se actualiza automáticamente el campo {@code lastContactAt} en el lead.
   */
  @Transactional
  public LeadActivityResponse addActivity(UUID leadId, CreateLeadActivityRequest request, UUID currentUserId) {
    UUID tenantId = TenantContext.getTenantId();
    Lead lead = leadRepository.findWithDetailsByIdAndTenantId(leadId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado con id: " + leadId));

    User currentUser = (currentUserId != null) ? userRepository.findById(currentUserId).orElse(null) : null;
    LeadActivity activity = new LeadActivity(
        tenantId,
        lead,
        currentUser,
        request.getActivityType(),
        request.getNotes() != null ? request.getNotes().trim() : null
    );

    if (request.getActivityType() == LeadActivityType.llamada
        || request.getActivityType() == LeadActivityType.whatsapp
        || request.getActivityType() == LeadActivityType.email) {
      lead.setLastContactAt(Instant.now());
      leadRepository.save(lead);
    }

    LeadActivity saved = leadActivityRepository.save(activity);
    return LeadActivityResponse.fromEntity(saved);
  }

  /**
   * Recupera el historial cronológico de actividades registradas con un prospecto.
   */
  @Transactional(readOnly = true)
  public List<LeadActivityResponse> getActivities(UUID leadId) {
    UUID tenantId = TenantContext.getTenantId();
    // Validar existencia en el tenant actual antes de listar
    leadRepository.findByIdAndTenantId(leadId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado con id: " + leadId));

    return leadActivityRepository.findByLeadIdAndTenantIdOrderByCreatedAtDescWithUser(leadId, tenantId)
        .stream()
        .map(LeadActivityResponse::fromEntity)
        .toList();
  }

  private User findUserInTenant(UUID userId, UUID tenantId) {
    return userRepository.findById(userId)
        .filter(u -> u.getTenant() != null && u.getTenant().getId().equals(tenantId))
        .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado en el tenant: " + userId));
  }
}
