package com.julio.odentix.odentix_backend.opportunity.listener;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
import com.julio.odentix.odentix_backend.appointment.event.AppointmentCancelledEvent;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.WaitlistEntryRepository;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.opportunity.service.OpportunityActionFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regla por evento: espacio disponible → oportunidad de negocio (FASE9-02).
 *
 * <p>Reacciona a {@link AppointmentCancelledEvent} (el mismo evento de FASE8-06):
 * si la cita cancelada tiene candidatos compatibles en lista de espera, el
 * espacio liberado es una oportunidad de reagendar y recuperar ingreso.
 *
 * <p>Idempotencia: si ya existe una oportunidad abierta ({@code abierta} o
 * {@code en_progreso}) para la misma cita, no se crea otra. Nota: la guarda es
 * por entidad, no por tipo — si la cita ya tenía una `cita_alto_riesgo`
 * abierta, no se duplica con `espacio_disponible` (ver nota en el roadmap de
 * FASE9-02).
 *
 * <p>Prioridad determinística (1 a 5) por valor del espacio:
 * <ul>
 *   <li>Valor ≥ $500.000 COP → prioridad 4 (Alta).</li>
 *   <li>Valor menor → prioridad 3 (Media-alta).</li>
 * </ul>
 *
 * <p>Vive en el módulo `opportunity` y escucha por eventos para no crear un
 * ciclo de dependencias con `appointment`. Corre en la misma transacción de la
 * cancelación.
 */
@Component
public class SlotOpportunityListener {

  private static final Logger log = LoggerFactory.getLogger(SlotOpportunityListener.class);

  /** Estados de oportunidad considerados "abiertos" para la verificación de idempotencia. */
  private static final List<OpportunityStatus> ESTADOS_ABIERTOS =
      List.of(OpportunityStatus.abierta, OpportunityStatus.en_progreso);

  /** Umbral de corte para prioridad alta: valor ≥ $500.000 COP. */
  private static final BigDecimal UMBRAL_ALTA = new BigDecimal("500000.00");

  private final AppointmentRepository appointmentRepository;
  private final WaitlistEntryRepository waitlistEntryRepository;
  private final OpportunityRepository opportunityRepository;
  private final OpportunityActionFactory actionFactory;

  public SlotOpportunityListener(
      AppointmentRepository appointmentRepository,
      WaitlistEntryRepository waitlistEntryRepository,
      OpportunityRepository opportunityRepository,
      OpportunityActionFactory actionFactory) {
    this.appointmentRepository = appointmentRepository;
    this.waitlistEntryRepository = waitlistEntryRepository;
    this.opportunityRepository = opportunityRepository;
    this.actionFactory = actionFactory;
  }

  /**
   * Crea la oportunidad de espacio disponible si la cancelación lo amerita.
   */
  @EventListener
  @Transactional
  public void onAppointmentCancelled(AppointmentCancelledEvent event) {
    Appointment cita = appointmentRepository
        .findByIdAndTenantId(event.appointmentId(), event.tenantId())
        .orElse(null);
    if (cita == null) {
      return;
    }

    UUID patientId = cita.getPatient() != null ? cita.getPatient().getId() : null;
    boolean hayCandidatos = !waitlistEntryRepository.findCompatibleCandidates(
        event.tenantId(),
        WaitlistStatus.activa,
        patientId,
        cita.getProcedureId(),
        cita.getStartsAt(),
        cita.getEndsAt()).isEmpty();
    if (!hayCandidatos) {
      return;
    }

    boolean yaExiste = opportunityRepository
        .existsByRelatedEntityTypeAndRelatedEntityIdAndStatusIn(
            "appointment", cita.getId(), ESTADOS_ABIERTOS);
    if (yaExiste) {
      return;
    }

    BigDecimal valor = cita.getEstimatedValueCop() != null
        ? cita.getEstimatedValueCop()
        : BigDecimal.ZERO;
    short prioridad = valor.compareTo(UMBRAL_ALTA) >= 0 ? (short) 4 : (short) 3;

    Opportunity oportunidad = new Opportunity(
        event.tenantId(),
        OpportunityType.espacio_disponible,
        prioridad);
    oportunidad.setRelatedEntityType("appointment");
    oportunidad.setRelatedEntityId(cita.getId());
    oportunidad.setEstimatedValueCop(valor);
    opportunityRepository.save(oportunidad);
    // FASE9-03: cada oportunidad trae sus acciones sugeridas.
    actionFactory.paraEspacio(oportunidad, cita);

    log.info("Oportunidad de espacio disponible creada para la cita cancelada {}.", cita.getId());
  }
}
