package com.julio.odentix.odentix_backend.appointment.repository;

import com.julio.odentix.odentix_backend.appointment.entity.WaitlistEntry;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link WaitlistEntry} (FASE3-06 y FASE3-07).
 */
@Repository
public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, UUID> {

  /**
   * Busca una entrada por ID dentro del tenant activo.
   *
   * <p>Filtra por {@code tenantId} explícito además del automático @TenantId
   * (defensa en profundidad, regla §5.2 de AGENTS.md).
   */
  Optional<WaitlistEntry> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Encuentra candidatos activos de la lista de espera compatibles con el horario
   * y procedimiento de una cita cancelada (recuperación de espacio, FASE3-07).
   *
   * <p>Filtra por:
   * <ul>
   *   <li>Tenant activo (defensa en profundidad).</li>
   *   <li>Estado activo ({@code WaitlistStatus.activa}).</li>
   *   <li>Excluye al paciente que canceló la cita.</li>
   *   <li>Procedimiento compatible (mismo {@code procedureId} o cualquiera si es nulo).</li>
   *   <li>Ventana deseada solapada con el intervalo liberado {@code [slotStart, slotEnd]}.</li>
   * </ul>
   *
   * @param tenantId tenant al que pertenece la cita.
   * @param status estado de las entradas a considerar (normalmente {@code WaitlistStatus.activa}).
   * @param excludedPatientId paciente de la cita cancelada (para no sugerirlo a sí mismo).
   * @param procedureId procedimiento de la cita (opcional).
   * @param slotStart inicio del horario liberado.
   * @param slotEnd fin del horario liberado.
   * @return lista de candidatos ordenados por orden de llegada (FIFO).
   */
  @Query("""
      SELECT w FROM WaitlistEntry w
      WHERE w.tenantId = :tenantId
        AND w.status = :status
        AND w.patient.id != :excludedPatientId
        AND (:procedureId IS NULL OR w.procedureId IS NULL OR w.procedureId = :procedureId)
        AND (w.desiredFrom IS NULL OR w.desiredFrom < :slotEnd)
        AND (w.desiredTo IS NULL OR w.desiredTo > :slotStart)
      ORDER BY w.createdAt ASC
  """)
  List<WaitlistEntry> findCompatibleCandidates(
      @Param("tenantId") UUID tenantId,
      @Param("status") WaitlistStatus status,
      @Param("excludedPatientId") UUID excludedPatientId,
      @Param("procedureId") UUID procedureId,
      @Param("slotStart") Instant slotStart,
      @Param("slotEnd") Instant slotEnd);
}
