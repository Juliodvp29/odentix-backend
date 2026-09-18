package com.julio.odentix.odentix_backend.crm.service;

import com.julio.odentix.odentix_backend.appointment.dto.AppointmentResponse;
import com.julio.odentix.odentix_backend.appointment.dto.CreateAppointmentRequest;
import com.julio.odentix.odentix_backend.appointment.service.AppointmentService;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.repository.UserRepository;
import com.julio.odentix.odentix_backend.crm.dto.ConvertLeadAppointmentData;
import com.julio.odentix.odentix_backend.crm.dto.ConvertLeadPatientData;
import com.julio.odentix.odentix_backend.crm.dto.ConvertLeadRequest;
import com.julio.odentix.odentix_backend.crm.dto.ConvertLeadResponse;
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
import com.julio.odentix.odentix_backend.patient.dto.PatientResponse;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para la gestión de prospectos comerciales y su embudo en el CRM (FASE5-02, FASE5-03).
 */
@Service
public class LeadService {

  private final LeadRepository leadRepository;
  private final LeadActivityRepository leadActivityRepository;
  private final UserRepository userRepository;
  private final PatientRepository patientRepository;
  private final AppointmentService appointmentService;

  public LeadService(
      LeadRepository leadRepository,
      LeadActivityRepository leadActivityRepository,
      UserRepository userRepository,
      PatientRepository patientRepository,
      AppointmentService appointmentService) {
    this.leadRepository = leadRepository;
    this.leadActivityRepository = leadActivityRepository;
    this.userRepository = userRepository;
    this.patientRepository = patientRepository;
    this.appointmentService = appointmentService;
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

  /**
   * Convierte un prospecto comercial en un paciente activo en la clínica,
   * y opcionalmente agenda su primera cita médica (FASE5-03).
   *
   * <p>Si el prospecto ya fue convertido previamente, la operación es idempotente
   * y devuelve los datos del paciente existente sin crear duplicados.
   */
  @Transactional
  public ConvertLeadResponse convertLead(UUID leadId, ConvertLeadRequest request, UUID currentUserId) {
    UUID tenantId = TenantContext.getTenantId();

    Lead lead = leadRepository.findWithDetailsByIdAndTenantId(leadId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Lead no encontrado con id: " + leadId));

    // Idempotencia: si el lead ya fue convertido a un paciente previamente
    if (lead.getConvertedPatient() != null) {
      Patient existingPatient = lead.getConvertedPatient();
      return new ConvertLeadResponse(
          lead.getId(),
          existingPatient.getId(),
          PatientResponse.fromEntity(existingPatient),
          null,
          null,
          true
      );
    }

    // Resolver datos del paciente a crear
    String firstName = null;
    String lastName = null;
    String phone = lead.getPhone();
    String email = lead.getEmail();
    String docType = null;
    String docNumber = null;
    LocalDate birthDate = null;
    String address = null;
    String emergencyContactName = null;
    String emergencyContactPhone = null;

    if (request != null && request.getPatient() != null) {
      ConvertLeadPatientData pData = request.getPatient();
      if (pData.getFirstName() != null && !pData.getFirstName().isBlank()) {
        firstName = pData.getFirstName().trim();
      }
      if (pData.getLastName() != null && !pData.getLastName().isBlank()) {
        lastName = pData.getLastName().trim();
      }
      if (pData.getPhone() != null && !pData.getPhone().isBlank()) {
        phone = pData.getPhone().trim();
      }
      if (pData.getEmail() != null && !pData.getEmail().isBlank()) {
        email = pData.getEmail().trim();
      }
      docType = pData.getDocumentType();
      docNumber = pData.getDocumentNumber();
      birthDate = pData.getBirthDate();
      address = pData.getAddress();
      emergencyContactName = pData.getEmergencyContactName();
      emergencyContactPhone = pData.getEmergencyContactPhone();
    }

    // Inferencia de nombres si no se indicaron explícitamente en el body
    if (firstName == null || lastName == null) {
      String fullName = (lead.getFullName() != null) ? lead.getFullName().trim() : "";
      int firstSpace = fullName.indexOf(' ');
      if (firstSpace > 0) {
        if (firstName == null) {
          firstName = fullName.substring(0, firstSpace).trim();
        }
        if (lastName == null) {
          lastName = fullName.substring(firstSpace + 1).trim();
        }
      } else {
        if (firstName == null) {
          firstName = fullName.isEmpty() ? "Prospecto" : fullName;
        }
        if (lastName == null) {
          lastName = ".";
        }
      }
    }

    // Crear y persistir el nuevo paciente en el tenant
    Patient patient = new Patient(tenantId, firstName, lastName);
    patient.setDocumentType(docType);
    patient.setDocumentNumber(docNumber);
    patient.setBirthDate(birthDate);
    patient.setPhone(phone);
    patient.setEmail(email);
    patient.setAddress(address);
    patient.setEmergencyContactName(emergencyContactName);
    patient.setEmergencyContactPhone(emergencyContactPhone);

    Patient savedPatient = patientRepository.save(patient);

    // Opcional: agendar primera cita médica
    AppointmentResponse appointmentResponse = null;
    UUID appointmentId = null;

    if (request != null && request.getAppointment() != null) {
      ConvertLeadAppointmentData aData = request.getAppointment();
      CreateAppointmentRequest apptReq = new CreateAppointmentRequest();
      apptReq.setPatientId(savedPatient.getId());
      apptReq.setProfessionalId(aData.getProfessionalId());
      apptReq.setRoomId(aData.getRoomId());
      apptReq.setProcedureId(aData.getProcedureId());
      apptReq.setStartsAt(aData.getStartsAt());
      apptReq.setEndsAt(aData.getEndsAt());
      apptReq.setEstimatedValueCop(
          aData.getEstimatedValueCop() != null ? aData.getEstimatedValueCop() : lead.getEstimatedValueCop());
      apptReq.setRiskLevel(aData.getRiskLevel());
      apptReq.setNotes(aData.getNotes());

      appointmentResponse = appointmentService.createAppointment(apptReq);
      appointmentId = appointmentResponse.getId();

      // Transición comercial: cita agendada
      lead.setStatus(LeadStatus.cita_agendada);
    } else {
      // Sin cita: si estaba en nuevo o contactado, avanza a calificado
      if (lead.getStatus() == LeadStatus.nuevo || lead.getStatus() == LeadStatus.contactado) {
        lead.setStatus(LeadStatus.calificado);
      }
    }

    // Vincular lead al paciente creado
    lead.setConvertedPatient(savedPatient);

    // Registrar actividad de trazabilidad en el prospecto
    StringBuilder notesBuilder = new StringBuilder("Lead convertido exitosamente a paciente: ")
        .append(savedPatient.getFirstName())
        .append(" ")
        .append(savedPatient.getLastName())
        .append(" (ID: ")
        .append(savedPatient.getId())
        .append(")");

    if (appointmentResponse != null) {
      notesBuilder.append(". Cita programada para ").append(appointmentResponse.getStartsAt());
    }

    User currentUser = (currentUserId != null) ? userRepository.findById(currentUserId).orElse(null) : null;
    LeadActivity activity = new LeadActivity(
        tenantId,
        lead,
        currentUser,
        LeadActivityType.nota,
        notesBuilder.toString()
    );
    leadActivityRepository.save(activity);

    leadRepository.save(lead);

    return new ConvertLeadResponse(
        lead.getId(),
        savedPatient.getId(),
        PatientResponse.fromEntity(savedPatient),
        appointmentId,
        appointmentResponse,
        false
    );
  }

  private User findUserInTenant(UUID userId, UUID tenantId) {
    return userRepository.findById(userId)
        .filter(u -> u.getTenant() != null && u.getTenant().getId().equals(tenantId))
        .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado en el tenant: " + userId));
  }
}

