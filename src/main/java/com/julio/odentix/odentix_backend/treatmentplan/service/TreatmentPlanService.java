package com.julio.odentix.odentix_backend.treatmentplan.service;

import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.treatmentplan.dto.CreateTreatmentPlanItemRequest;
import com.julio.odentix.odentix_backend.treatmentplan.dto.CreateTreatmentPlanRequest;
import com.julio.odentix.odentix_backend.treatmentplan.dto.TreatmentPlanResponse;
import com.julio.odentix.odentix_backend.treatmentplan.dto.UpdateTreatmentPlanRequest;
import com.julio.odentix.odentix_backend.treatmentplan.dto.UpdateTreatmentPlanStatusRequest;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanItem;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para la gestión de planes de tratamiento odontológico y su ciclo de vida (FASE4-02).
 */
@Service
public class TreatmentPlanService {

  private final TreatmentPlanRepository treatmentPlanRepository;
  private final PatientRepository patientRepository;
  private final ProfessionalRepository professionalRepository;

  /**
   * Transiciones de estado permitidas (FASE4-02, sección 8.9 del doc de arquitectura).
   *
   * <p>Máquina de estados simple en el service layer:
   * <ul>
   *   <li>borrador → presentado, abandonado</li>
   *   <li>presentado → en_decision, aceptado, rechazado, pospuesto, abandonado</li>
   *   <li>en_decision → aceptado, rechazado, pospuesto, abandonado</li>
   *   <li>pospuesto → presentado, en_decision, aceptado, rechazado, abandonado</li>
   *   <li>aceptado → en_ejecucion, abandonado</li>
   *   <li>en_ejecucion → completado, abandonado</li>
   *   <li>Estados terminales: completado, rechazado, abandonado</li>
   * </ul>
   */
  private static final Map<TreatmentPlanStatus, Set<TreatmentPlanStatus>> TRANSICIONES_PERMITIDAS;

  static {
    Map<TreatmentPlanStatus, Set<TreatmentPlanStatus>> map = new EnumMap<>(TreatmentPlanStatus.class);
    map.put(TreatmentPlanStatus.borrador, Set.of(TreatmentPlanStatus.presentado, TreatmentPlanStatus.abandonado));
    map.put(TreatmentPlanStatus.presentado, Set.of(
        TreatmentPlanStatus.en_decision,
        TreatmentPlanStatus.aceptado,
        TreatmentPlanStatus.rechazado,
        TreatmentPlanStatus.pospuesto,
        TreatmentPlanStatus.abandonado));
    map.put(TreatmentPlanStatus.en_decision, Set.of(
        TreatmentPlanStatus.aceptado,
        TreatmentPlanStatus.rechazado,
        TreatmentPlanStatus.pospuesto,
        TreatmentPlanStatus.abandonado));
    map.put(TreatmentPlanStatus.pospuesto, Set.of(
        TreatmentPlanStatus.presentado,
        TreatmentPlanStatus.en_decision,
        TreatmentPlanStatus.aceptado,
        TreatmentPlanStatus.rechazado,
        TreatmentPlanStatus.abandonado));
    map.put(TreatmentPlanStatus.aceptado, Set.of(
        TreatmentPlanStatus.en_ejecucion,
        TreatmentPlanStatus.abandonado));
    map.put(TreatmentPlanStatus.en_ejecucion, Set.of(
        TreatmentPlanStatus.completado,
        TreatmentPlanStatus.abandonado));
    map.put(TreatmentPlanStatus.completado, Set.of());
    map.put(TreatmentPlanStatus.rechazado, Set.of());
    map.put(TreatmentPlanStatus.abandonado, Set.of());
    TRANSICIONES_PERMITIDAS = Map.copyOf(map);
  }

  public TreatmentPlanService(
      TreatmentPlanRepository treatmentPlanRepository,
      PatientRepository patientRepository,
      ProfessionalRepository professionalRepository) {
    this.treatmentPlanRepository = treatmentPlanRepository;
    this.patientRepository = patientRepository;
    this.professionalRepository = professionalRepository;
  }

  /**
   * Crea un nuevo plan de tratamiento con sus ítems iniciales en estado {@code borrador}.
   *
   * @param request datos del plan y procedimientos asociados.
   * @return plan de tratamiento creado.
   */
  @Transactional
  public TreatmentPlanResponse createTreatmentPlan(CreateTreatmentPlanRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Patient patient = patientRepository.findByIdAndTenantId(request.getPatientId(), tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado: " + request.getPatientId()));

    Professional professional = null;
    if (request.getProfessionalId() != null) {
      professional = professionalRepository.findByIdAndTenantId(request.getProfessionalId(), tenantId)
          .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado: " + request.getProfessionalId()));
    }

    TreatmentPlan plan = new TreatmentPlan(tenantId, patient, professional, request.getDiagnosis());

    if (request.getItems() != null) {
      for (CreateTreatmentPlanItemRequest itemReq : request.getItems()) {
        validarPreciosItem(itemReq);
        TreatmentPlanItem item = new TreatmentPlanItem(
            tenantId,
            plan,
            itemReq.getProcedureId(),
            itemReq.getToothNumber(),
            itemReq.getPriceCop(),
            itemReq.getDiscountCop()
        );
        plan.addItem(item);
      }
    }

    TreatmentPlan saved = treatmentPlanRepository.save(plan);
    return TreatmentPlanResponse.fromEntity(saved);
  }

  /**
   * Consulta un plan de tratamiento por su identificador único dentro del tenant activo.
   *
   * @param id identificador del plan.
   * @return plan con detalle de sus ítems.
   */
  @Transactional(readOnly = true)
  public TreatmentPlanResponse getTreatmentPlan(UUID id) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    TreatmentPlan plan = treatmentPlanRepository.findWithItemsByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Plan de tratamiento no encontrado: " + id));

    return TreatmentPlanResponse.fromEntity(plan);
  }

  /**
   * Lista planes de tratamiento aplicando filtros opcionales por paciente y estado.
   *
   * @param patientId filtro opcional por paciente.
   * @param status filtro opcional por estado.
   * @return lista de planes de tratamiento encontrados.
   */
  @Transactional(readOnly = true)
  public List<TreatmentPlanResponse> listTreatmentPlans(UUID patientId, TreatmentPlanStatus status) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    List<TreatmentPlan> plans;

    if (patientId != null && status != null) {
      plans = treatmentPlanRepository.findByPatientIdAndStatusAndTenantId(patientId, status, tenantId);
    } else if (patientId != null) {
      plans = treatmentPlanRepository.findByPatientIdAndTenantId(patientId, tenantId);
    } else if (status != null) {
      plans = treatmentPlanRepository.findByStatusAndTenantId(status, tenantId);
    } else {
      plans = treatmentPlanRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    List<TreatmentPlanResponse> responses = new ArrayList<>();
    for (TreatmentPlan plan : plans) {
      responses.add(TreatmentPlanResponse.fromEntity(plan));
    }
    return responses;
  }

  /**
   * Actualiza los datos o ítems de un plan de tratamiento.
   *
   * <p>Los ítems solo pueden modificarse mientras el plan se encuentre en estado {@code borrador}.
   *
   * @param id identificador del plan.
   * @param request datos a actualizar.
   * @return plan actualizado.
   */
  @Transactional
  public TreatmentPlanResponse updateTreatmentPlan(UUID id, UpdateTreatmentPlanRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    TreatmentPlan plan = treatmentPlanRepository.findWithItemsByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Plan de tratamiento no encontrado: " + id));

    if (request.getDiagnosis() != null) {
      plan.setDiagnosis(request.getDiagnosis());
    }

    if (request.getProfessionalId() != null) {
      Professional professional = professionalRepository.findByIdAndTenantId(request.getProfessionalId(), tenantId)
          .orElseThrow(() -> new ResourceNotFoundException("Profesional no encontrado: " + request.getProfessionalId()));
      plan.setProfessional(professional);
    }

    if (request.getItems() != null) {
      if (plan.getStatus() != TreatmentPlanStatus.borrador) {
        throw new IllegalStateException(
            "Solo se pueden modificar los procedimientos de un plan en estado 'borrador'. El estado actual es: " + plan.getStatus());
      }

      plan.getItems().clear();
      for (CreateTreatmentPlanItemRequest itemReq : request.getItems()) {
        validarPreciosItem(itemReq);
        TreatmentPlanItem item = new TreatmentPlanItem(
            tenantId,
            plan,
            itemReq.getProcedureId(),
            itemReq.getToothNumber(),
            itemReq.getPriceCop(),
            itemReq.getDiscountCop()
        );
        plan.addItem(item);
      }
    }

    TreatmentPlan saved = treatmentPlanRepository.save(plan);
    return TreatmentPlanResponse.fromEntity(saved);
  }

  /**
   * Avanza el plan de tratamiento a un nuevo estado según las reglas de la máquina de estados.
   *
   * @param id identificador del plan dentro del tenant activo.
   * @param request nuevo estado deseado.
   * @return plan actualizado.
   */
  @Transactional
  public TreatmentPlanResponse updateStatus(UUID id, UpdateTreatmentPlanStatusRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    TreatmentPlan plan = treatmentPlanRepository.findWithItemsByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException("Plan de tratamiento no encontrado: " + id));

    TreatmentPlanStatus actual = plan.getStatus();
    TreatmentPlanStatus destino = request.getStatus();

    if (!actual.equals(destino)) {
      Set<TreatmentPlanStatus> permitidos = TRANSICIONES_PERMITIDAS.getOrDefault(actual, Set.of());
      if (!permitidos.contains(destino)) {
        String detalle = permitidos.isEmpty()
            ? "ninguna (es un estado terminal)"
            : "solo " + permitidos;
        throw new IllegalArgumentException(
            "No se puede pasar de '" + actual + "' a '" + destino
                + "'. Transiciones permitidas desde '" + actual + "': " + detalle);
      }

      plan.setStatus(destino);

      // Trazabilidad de presentación y seguimiento
      if (destino == TreatmentPlanStatus.presentado) {
        if (plan.getPresentedAt() == null) {
          plan.setPresentedAt(Instant.now());
        }
        plan.setLastContactAt(Instant.now());
      } else if (destino == TreatmentPlanStatus.en_decision) {
        plan.setLastContactAt(Instant.now());
      }
    }

    TreatmentPlan saved = treatmentPlanRepository.save(plan);
    return TreatmentPlanResponse.fromEntity(saved);
  }

  private void validarPreciosItem(CreateTreatmentPlanItemRequest item) {
    if (item.getDiscountCop() != null && item.getPriceCop() != null
        && item.getDiscountCop().compareTo(item.getPriceCop()) > 0) {
      throw new IllegalArgumentException("El descuento no puede superar el precio del procedimiento");
    }
  }
}
