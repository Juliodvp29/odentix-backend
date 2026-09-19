package com.julio.odentix.odentix_backend.task.service;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.RiskLevel;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistEntry;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
import com.julio.odentix.odentix_backend.appointment.event.AppointmentCancelledEvent;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.WaitlistEntryRepository;
import com.julio.odentix.odentix_backend.task.entity.Task;
import com.julio.odentix.odentix_backend.task.entity.TaskPriority;
import com.julio.odentix.odentix_backend.task.repository.TaskRepository;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Automatización de recuperación de espacio (FASE8-06).
 *
 * <p>Reacciona a {@link AppointmentCancelledEvent}: si la cita cancelada era
 * de alto valor ({@code risk_level = alto}, mismo criterio de FASE3-07) y hay
 * candidatos compatibles en lista de espera, crea automáticamente una tarea
 * para recepción con el detalle de a quién contactar.
 *
 * <p>Vive en el módulo `task` y escucha por eventos para no crear un ciclo de
 * dependencias con `appointment`. Corre en la misma transacción de la
 * cancelación: si la tarea no se puede crear, la cancelación tampoco queda
 * (falla local de BD, no de proveedor externo — distinto al principio de
 * FASE8-04, que aplica a canales que pueden caerse).
 */
@Component
public class SlotRecoveryListener {

  private static final Logger log = LoggerFactory.getLogger(SlotRecoveryListener.class);

  private final AppointmentRepository appointmentRepository;
  private final WaitlistEntryRepository waitlistEntryRepository;
  private final TaskRepository taskRepository;

  public SlotRecoveryListener(
      AppointmentRepository appointmentRepository,
      WaitlistEntryRepository waitlistEntryRepository,
      TaskRepository taskRepository) {
    this.appointmentRepository = appointmentRepository;
    this.waitlistEntryRepository = waitlistEntryRepository;
    this.taskRepository = taskRepository;
  }

  /**
   * Crea la tarea de recuperación si la cancelación lo amerita.
   */
  @EventListener
  @Transactional
  public void onAppointmentCancelled(AppointmentCancelledEvent event) {
    Appointment cita = appointmentRepository
        .findByIdAndTenantId(event.appointmentId(), event.tenantId())
        .orElse(null);
    if (cita == null || cita.getRiskLevel() != RiskLevel.alto) {
      return;
    }

    UUID patientId = cita.getPatient() != null ? cita.getPatient().getId() : null;
    List<WaitlistEntry> candidatos = waitlistEntryRepository.findCompatibleCandidates(
        event.tenantId(),
        WaitlistStatus.activa,
        patientId,
        cita.getProcedureId(),
        cita.getStartsAt(),
        cita.getEndsAt());
    if (candidatos.isEmpty()) {
      return;
    }

    String detalle = candidatos.stream()
        .map(c -> "- " + c.getPatient().getFirstName() + " " + c.getPatient().getLastName()
            + " (tel: " + c.getPatient().getPhone() + ")")
        .collect(Collectors.joining("\n"));

    Task tarea = new Task(event.tenantId(), "Recuperar espacio liberado de alto valor");
    tarea.setDescription(
        "Se canceló una cita de alto valor del " + cita.getStartsAt() + ". "
            + "Contactar a estos candidatos de lista de espera:\n" + detalle);
    tarea.setRelatedEntityType("appointment");
    tarea.setRelatedEntityId(cita.getId());
    tarea.setDueAt(cita.getStartsAt());
    tarea.setPriority(TaskPriority.alta);
    taskRepository.save(tarea);

    log.info("Tarea de recuperación creada para la cita cancelada {} ({} candidatos).",
        cita.getId(), candidatos.size());
  }
}
