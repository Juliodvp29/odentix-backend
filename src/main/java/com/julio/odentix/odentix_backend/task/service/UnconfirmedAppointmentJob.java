package com.julio.odentix.odentix_backend.task.service;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.task.entity.Task;
import com.julio.odentix.odentix_backend.task.entity.TaskPriority;
import com.julio.odentix.odentix_backend.task.entity.TaskStatus;
import com.julio.odentix.odentix_backend.task.repository.TaskRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regla automática: cita sin confirmar próxima a su horario → tarea para
 * recepción (FASE8-02).
 *
 * <p>Condición: cita en estado {@code programada} cuyo inicio está dentro de
 * las próximas 24 horas. Idempotencia: si ya existe una tarea abierta
 * ({@code pendiente} o {@code en_progreso}) vinculada a la cita, no se crea
 * otra — correr el job dos veces seguidas no duplica.
 *
 * <p>La tarea se crea <b>sin responsable asignado</b>: no hay un usuario de
 * recepción determinístico al cual asignarla (elegir "el primero que aparezca"
 * sería sorpresivo y frágil). Queda visible en el listado general con prioridad
 * {@code alta} y vencimiento en el horario de la cita.
 *
 * <p>Corre en contexto de sistema (sin request → {@code ROOT_TENANT_ID} sin
 * filtro de tenant): una sola corrida cubre todas las clínicas, igual que el
 * job de cuotas vencidas de FASE6-03. Cada tarea hereda el {@code tenantId} de
 * su cita, así que el aislamiento se mantiene dato por dato.
 */
@Service
public class UnconfirmedAppointmentJob {

  private static final Logger log = LoggerFactory.getLogger(UnconfirmedAppointmentJob.class);

  static final Duration HORIZONTE = Duration.ofHours(24);

  private static final List<TaskStatus> ESTADOS_ABIERTOS =
      List.of(TaskStatus.pendiente, TaskStatus.en_progreso);

  private final AppointmentRepository appointmentRepository;
  private final TaskRepository taskRepository;

  public UnconfirmedAppointmentJob(
      AppointmentRepository appointmentRepository,
      TaskRepository taskRepository) {
    this.appointmentRepository = appointmentRepository;
    this.taskRepository = taskRepository;
  }

  /**
   * Ejecuta la regla y crea las tareas correspondientes.
   *
   * @return total de tareas creadas en esta corrida.
   */
  @Transactional
  public int execute() {
    log.info("Iniciando job de citas sin confirmar...");
    Instant ahora = Instant.now();
    List<Appointment> candidatas = appointmentRepository.findByStatusAndStartsAtBetween(
        AppointmentStatus.programada, ahora, ahora.plus(HORIZONTE));

    int creadas = 0;
    for (Appointment cita : candidatas) {
      boolean yaExiste = taskRepository
          .existsByRelatedEntityTypeAndRelatedEntityIdAndStatusIn(
              "appointment", cita.getId(), ESTADOS_ABIERTOS);
      if (yaExiste) {
        continue;
      }
      Task tarea = new Task(
          cita.getTenantId(),
          "Confirmar cita próxima sin confirmar");
      tarea.setDescription(
          "La cita de las " + cita.getStartsAt() + " sigue sin confirmar a menos de 24h. "
              + "Contactar al paciente para confirmar o liberar el espacio.");
      tarea.setRelatedEntityType("appointment");
      tarea.setRelatedEntityId(cita.getId());
      tarea.setDueAt(cita.getStartsAt());
      tarea.setPriority(TaskPriority.alta);
      taskRepository.save(tarea);
      creadas++;
    }

    log.info("Job de citas sin confirmar completado: {} tareas creadas.", creadas);
    return creadas;
  }

  /**
   * Disparador programado cada hora con cron configurable.
   */
  @Scheduled(cron = "${odentix.jobs.unconfirmed-appointments.cron:0 0 * * * *}")
  public void runScheduledJob() {
    execute();
  }
}
