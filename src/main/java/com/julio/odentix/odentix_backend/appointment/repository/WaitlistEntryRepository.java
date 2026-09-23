package com.julio.odentix.odentix_backend.appointment.repository;

import com.julio.odentix.odentix_backend.appointment.entity.WaitlistEntry;
import com.julio.odentix.odentix_backend.appointment.entity.WaitlistStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
   * (defensa en profundidad por tenant).
   */
  Optional<WaitlistEntry> findByIdAndTenantId(UUID id, UUID tenantId);

  @Query("""
      SELECT w FROM WaitlistEntry w
      JOIN FETCH w.patient
      WHERE w.id = :id AND w.tenantId = :tenantId
      """)
  Optional<WaitlistEntry> findWithPatientByIdAndTenantId(
      @Param("id") UUID id, @Param("tenantId") UUID tenantId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("""
      SELECT w FROM WaitlistEntry w
      JOIN FETCH w.patient
      WHERE w.id = :id AND w.tenantId = :tenantId
      """)
  Optional<WaitlistEntry> findWithPatientByIdAndTenantIdForUpdate(
      @Param("id") UUID id, @Param("tenantId") UUID tenantId);

  @Query("""
      SELECT w FROM WaitlistEntry w
      JOIN FETCH w.patient
      WHERE w.tenantId = :tenantId
      """)
  Page<WaitlistEntry> searchAll(
      @Param("tenantId") UUID tenantId,
      Pageable pageable);

  @Query("""
      SELECT w FROM WaitlistEntry w
      JOIN FETCH w.patient
      WHERE w.tenantId = :tenantId
        AND w.status = :status
      """)
  Page<WaitlistEntry> searchByStatus(
      @Param("tenantId") UUID tenantId,
      @Param("status") WaitlistStatus status,
      Pageable pageable);

  @Query("""
      SELECT w FROM WaitlistEntry w
      JOIN FETCH w.patient p
      WHERE w.tenantId = :tenantId
        AND (
          CAST(function('immutable_unaccent', lower(concat(p.firstName, ' ', p.lastName))) AS string)
            LIKE concat('%', cast(function('immutable_unaccent', lower(cast(:query as string))) AS string), '%')
          OR CAST(function('immutable_unaccent', lower(concat(p.lastName, ' ', p.firstName))) AS string)
            LIKE concat('%', cast(function('immutable_unaccent', lower(cast(:query as string))) AS string), '%')
          OR (p.phone IS NOT NULL AND lower(p.phone) LIKE concat('%', lower(cast(:query as string)), '%'))
        )
      """)
  Page<WaitlistEntry> searchByText(
      @Param("tenantId") UUID tenantId,
      @Param("query") String query,
      Pageable pageable);

  @Query("""
      SELECT w FROM WaitlistEntry w
      JOIN FETCH w.patient p
      WHERE w.tenantId = :tenantId
        AND w.status = :status
        AND (
          CAST(function('immutable_unaccent', lower(concat(p.firstName, ' ', p.lastName))) AS string)
            LIKE concat('%', cast(function('immutable_unaccent', lower(cast(:query as string))) AS string), '%')
          OR CAST(function('immutable_unaccent', lower(concat(p.lastName, ' ', p.firstName))) AS string)
            LIKE concat('%', cast(function('immutable_unaccent', lower(cast(:query as string))) AS string), '%')
          OR (p.phone IS NOT NULL AND lower(p.phone) LIKE concat('%', lower(cast(:query as string)), '%'))
        )
      """)
  Page<WaitlistEntry> searchByStatusAndText(
      @Param("tenantId") UUID tenantId,
      @Param("status") WaitlistStatus status,
      @Param("query") String query,
      Pageable pageable);

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
      JOIN FETCH w.patient
      WHERE w.tenantId = :tenantId
        AND w.status = :status
        AND w.patient.id != :excludedPatientId
        AND (:procedureId IS NULL OR w.procedureId IS NULL OR w.procedureId = :procedureId)
        AND (w.desiredFrom IS NULL OR w.desiredFrom < :slotEnd)
        AND (w.desiredTo IS NULL OR w.desiredTo > :slotStart)
      ORDER BY w.createdAt ASC, w.id ASC
  """)
  List<WaitlistEntry> findCompatibleCandidates(
      @Param("tenantId") UUID tenantId,
      @Param("status") WaitlistStatus status,
      @Param("excludedPatientId") UUID excludedPatientId,
      @Param("procedureId") UUID procedureId,
      @Param("slotStart") Instant slotStart,
      @Param("slotEnd") Instant slotEnd);
}

