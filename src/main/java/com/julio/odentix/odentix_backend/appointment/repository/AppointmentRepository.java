package com.julio.odentix.odentix_backend.appointment.repository;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link Appointment} (FASE3-02).
 *
 * <p>Al heredar de {@link JpaRepository} sobre una {@link com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity},
 * todas las operaciones quedan automáticamente aisladas por el {@code tenant_id}
 * activo en el contexto gracias a {@code @TenantId} de Hibernate 6.
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

  /**
   * Obtiene las citas de un paciente específico.
   *
   * @param patientId identificador del paciente.
   * @return lista de citas asociadas.
   */
  List<Appointment> findByPatientId(UUID patientId);

  /**
   * Obtiene las citas asignadas a un profesional específico.
   *
   * @param professionalId identificador del profesional.
   * @return lista de citas del profesional.
   */
  List<Appointment> findByProfessionalId(UUID professionalId);

  /**
   * Obtiene las citas que inician dentro de un rango de tiempo dado.
   *
   * @param start inicio del intervalo temporal.
   * @param end fin del intervalo temporal.
   * @return lista de citas comprendidas.
   */
  List<Appointment> findByStartsAtBetween(Instant start, Instant end);

  /**
   * Citas en un estado que inician dentro de un rango (insumo de la regla
   * automática de FASE8-02: citas sin confirmar próximas a su horario).
   */
  List<Appointment> findByStatusAndStartsAtBetween(
      AppointmentStatus status, Instant start, Instant end);

  /**
   * Obtiene las citas asignadas a un consultorio específico.
   *
   * @param roomId identificador del consultorio.
   * @return lista de citas programadas en ese consultorio.
   */
  List<Appointment> findByRoomId(UUID roomId);

  /**
   * Busca una cita por ID dentro del tenant activo (FASE3-04).
   *
   * <p>Filtra por {@code tenantId} explícito además del automático @TenantId
   * (defensa en profundidad, regla §5.2 de AGENTS.md): una cita de otro
   * tenant resulta invisible aunque se conozca su ID directo.
   */
  Optional<Appointment> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Agenda de un rango de fechas, ordenada por hora de inicio (FASE3-03).
   *
   * <p>Filtra por {@code tenantId} explícito además del automático @TenantId
   * (defensa en profundidad, regla §5.2 de AGENTS.md).
   */
  List<Appointment> findByTenantIdAndStartsAtBetweenOrderByStartsAtAsc(
      UUID tenantId, Instant from, Instant to);

  /**
   * Agenda de un profesional en un rango de fechas, ordenada por hora de
   * inicio (FASE3-03). Mismo doble filtro de tenant que el método anterior.
   */
  List<Appointment> findByTenantIdAndProfessionalIdAndStartsAtBetweenOrderByStartsAtAsc(
      UUID tenantId, UUID professionalId, Instant from, Instant to);

  /**
   * Suma del valor estimado y conteo de citas de un rango (FASE3-05).
   *
   * <p>Query manual: filtra por {@code tenant_id} explícito aunque el
   * filtro automático @TenantId ya lo cubra (defensa en profundidad, regla
   * §5.2 de AGENTS.md — ninguna sustituye a la otra). Devuelve una fila
   * {@code [total, conteo]}; sin GROUP BY siempre hay exactamente una fila
   * y el total es 0 (no null) si no hay citas.
   *
   * <p>Se devuelve {@code List<Object[]>} y no {@code Object[]} directo:
   * Spring Data envuelve el resultado escalar múltiple y el casteo directo
   * falla en tiempo de ejecución.
   */
  @Query("""
      SELECT COALESCE(SUM(a.estimatedValueCop), 0), COUNT(a)
      FROM Appointment a
      WHERE a.tenantId = :tenantId
        AND a.startsAt BETWEEN :from AND :to
        AND a.status NOT IN :excludedStatuses
      """)
  List<Object[]> sumAndCountByTenantAndRange(
      @Param("tenantId") UUID tenantId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      @Param("excludedStatuses") List<AppointmentStatus> excludedStatuses);
}
