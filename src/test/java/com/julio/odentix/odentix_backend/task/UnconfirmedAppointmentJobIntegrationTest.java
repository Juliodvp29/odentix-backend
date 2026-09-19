package com.julio.odentix.odentix_backend.task;

import static org.assertj.core.api.Assertions.assertThat;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.task.entity.TaskPriority;
import com.julio.odentix.odentix_backend.task.entity.TaskStatus;
import com.julio.odentix.odentix_backend.task.repository.TaskRepository;
import com.julio.odentix.odentix_backend.task.service.UnconfirmedAppointmentJob;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de integración para {@link UnconfirmedAppointmentJob} (FASE8-02)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre el DoD del ticket:
 * <ul>
 *   <li>Una cita {@code programada} a menos de 24h genera automáticamente una
 *       tarea para recepción, sin intervención manual.</li>
 *   <li>Correr el job dos veces seguidas no duplica la tarea.</li>
 *   <li>No genera tareas para citas lejanas, ya confirmadas o pasadas.</li>
 *   <li>El job cubre todas las clínicas y cada tarea hereda el tenant de su
 *       cita (aislamiento dato por dato).</li>
 * </ul>
 *
 * <p>El job se invoca directamente (precedente de FASE6-03): el `@Scheduled`
 * solo define la frecuencia, la lógica vive en {@code execute()}.
 */
class UnconfirmedAppointmentJobIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private UnconfirmedAppointmentJob job;

  @Autowired
  private AppointmentRepository appointmentRepository;

  @Autowired
  private ProfessionalRepository professionalRepository;

  @Autowired
  private PatientRepository patientRepository;

  @Autowired
  private TaskRepository taskRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;
  private Appointment citaProximaA;
  private Appointment citaProximaB;
  private Appointment citaLejanaA;
  private Appointment citaConfirmadaA;
  private Appointment citaPasadaA;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Job Alfa", "980111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Job Beta", "981333444-2"));

    TenantContext.setTenantId(tenantA.getId());
    try {
      Patient pacienteA = patientRepository.save(new Patient(tenantA.getId(), "Luis", "Pardo"));
      Professional profesionalA = professionalRepository.saveAndFlush(
          new Professional(tenantA.getId(), "Dr. Job Alfa"));
      // Truncado a milisegundos: PG guarda microsegundos y la igualdad exacta
      // de dueAt fallaría por redondeo de nanos.
      Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
      // Candidata: programada a menos de 24h.
      citaProximaA = appointmentRepository.saveAndFlush(new Appointment(
          tenantA.getId(), pacienteA, profesionalA,
          ahora.plus(5, ChronoUnit.HOURS), ahora.plus(6, ChronoUnit.HOURS)));
      // Programada pero lejana (> 24h) → no genera.
      citaLejanaA = appointmentRepository.saveAndFlush(new Appointment(
          tenantA.getId(), pacienteA, profesionalA,
          ahora.plus(30, ChronoUnit.HOURS), ahora.plus(31, ChronoUnit.HOURS)));
      // Próxima pero ya confirmada → no genera.
      citaConfirmadaA = new Appointment(
          tenantA.getId(), pacienteA, profesionalA,
          ahora.plus(7, ChronoUnit.HOURS), ahora.plus(8, ChronoUnit.HOURS));
      citaConfirmadaA.setStatus(AppointmentStatus.confirmada);
      citaConfirmadaA = appointmentRepository.saveAndFlush(citaConfirmadaA);
      // Programada pero pasada → no genera.
      citaPasadaA = appointmentRepository.saveAndFlush(new Appointment(
          tenantA.getId(), pacienteA, profesionalA,
          ahora.minus(3, ChronoUnit.HOURS), ahora.minus(2, ChronoUnit.HOURS)));
    } finally {
      TenantContext.clear();
    }

    TenantContext.setTenantId(tenantB.getId());
    try {
      Patient pacienteB = patientRepository.save(new Patient(tenantB.getId(), "Sara", "Mora"));
      Professional profesionalB = professionalRepository.saveAndFlush(
          new Professional(tenantB.getId(), "Dra. Job Beta"));
      Instant ahora = Instant.now().truncatedTo(ChronoUnit.MILLIS);
      citaProximaB = appointmentRepository.saveAndFlush(new Appointment(
          tenantB.getId(), pacienteB, profesionalB,
          ahora.plus(5, ChronoUnit.HOURS), ahora.plus(6, ChronoUnit.HOURS)));
    } finally {
      TenantContext.clear();
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers: la BD se comparte entre clases de test y el job es global, así
  // que todas las aserciones se acotan a las citas propias de este test
  // (nunca a conteos globales).
  // ---------------------------------------------------------------------------

  private static final List<TaskStatus> ABIERTOS =
      List.of(TaskStatus.pendiente, TaskStatus.en_progreso);

  private boolean tieneTareaAbierta(UUID citaId) {
    return taskRepository.existsByRelatedEntityTypeAndRelatedEntityIdAndStatusIn(
        "appointment", citaId, ABIERTOS);
  }

  private com.julio.odentix.odentix_backend.task.entity.Task tareaAbiertaDe(UUID citaId) {
    TenantContext.clear();
    try {
      return taskRepository.findAll().stream()
          .filter(t -> citaId.equals(t.getRelatedEntityId())
              && (t.getStatus() == TaskStatus.pendiente
                  || t.getStatus() == TaskStatus.en_progreso))
          .findFirst()
          .orElseThrow(() -> new AssertionError("Sin tarea abierta para " + citaId));
    } finally {
      TenantContext.clear();
    }
  }

  private long contarTareasDe(UUID citaId) {
    TenantContext.clear();
    try {
      return taskRepository.findAll().stream()
          .filter(t -> citaId.equals(t.getRelatedEntityId()))
          .count();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void generaTareasParaCitasProximasSinConfirmar() {
    // Sin contexto de tenant: el job corre como sistema y cubre todo.
    TenantContext.clear();
    job.execute();

    // Candidatas de ambos tenants tienen tarea abierta con los campos esperados.
    assertThat(tieneTareaAbierta(citaProximaA.getId())).isTrue();
    assertThat(tieneTareaAbierta(citaProximaB.getId())).isTrue();

    var tareaA = tareaAbiertaDe(citaProximaA.getId());
    assertThat(tareaA.getTenantId()).isEqualTo(tenantA.getId());
    assertThat(tareaA.getStatus()).isEqualTo(TaskStatus.pendiente);
    assertThat(tareaA.getPriority()).isEqualTo(TaskPriority.alta);
    assertThat(tareaA.getRelatedEntityType()).isEqualTo("appointment");
    assertThat(tareaA.getDueAt()).isEqualTo(citaProximaA.getStartsAt());
    assertThat(tareaA.getAssignedTo()).isNull();

    var tareaB = tareaAbiertaDe(citaProximaB.getId());
    assertThat(tareaB.getTenantId()).isEqualTo(tenantB.getId());

    // No candidatas: nunca tienen tarea.
    assertThat(tieneTareaAbierta(citaLejanaA.getId())).isFalse();
    assertThat(tieneTareaAbierta(citaConfirmadaA.getId())).isFalse();
    assertThat(tieneTareaAbierta(citaPasadaA.getId())).isFalse();
  }

  @Test
  void segundaCorridaNoDuplicaTareasAbiertas() {
    // Los IDs de mis citas son únicos por test (setUp por método), así que
    // los deltas por cita son deterministas aunque el retorno de execute()
    // cuente creaciones globales de otros suites.
    long antesA = contarTareasDe(citaProximaA.getId());
    long antesB = contarTareasDe(citaProximaB.getId());

    TenantContext.clear();
    job.execute();
    assertThat(contarTareasDe(citaProximaA.getId())).isEqualTo(antesA + 1);
    assertThat(contarTareasDe(citaProximaB.getId())).isEqualTo(antesB + 1);

    // Segunda corrida: ninguna tarea nueva para mis citas.
    TenantContext.clear();
    job.execute();
    assertThat(contarTareasDe(citaProximaA.getId())).isEqualTo(antesA + 1);
    assertThat(contarTareasDe(citaProximaB.getId())).isEqualTo(antesB + 1);

    // Si la tarea se completó, la próxima corrida sí genera una nueva
    // (la guarda anti-duplicado solo cubre tareas abiertas).
    var abierta = tareaAbiertaDe(citaProximaA.getId());
    TenantContext.setTenantId(tenantA.getId());
    try {
      abierta.setStatus(TaskStatus.completada);
      taskRepository.saveAndFlush(abierta);
    } finally {
      TenantContext.clear();
    }

    TenantContext.clear();
    job.execute();
    assertThat(contarTareasDe(citaProximaA.getId())).isEqualTo(antesA + 2);
  }
}
