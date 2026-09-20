package com.julio.odentix.odentix_backend.treatmentplan.repository;

import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link TreatmentPlan} (FASE4-01).
 */
@Repository
public interface TreatmentPlanRepository extends JpaRepository<TreatmentPlan, UUID> {

  /**
   * Busca un plan de tratamiento por su ID y tenant.
   *
   * <p>Filtra explícitamente por {@code tenantId} además del filtro automático
   * de {@code @TenantId} (defensa en profundidad, regla de aislamiento multi-tenant del proyecto).
   */
  Optional<TreatmentPlan> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Busca todos los planes de tratamiento de un paciente dentro de un tenant.
   */
  List<TreatmentPlan> findByPatientIdAndTenantId(UUID patientId, UUID tenantId);

  /**
   * Busca planes de tratamiento por su estado dentro de un tenant.
   */
  List<TreatmentPlan> findByStatusAndTenantId(TreatmentPlanStatus status, UUID tenantId);

  /**
   * Busca planes de tratamiento de un paciente filtrados por estado dentro de un tenant.
   */
  List<TreatmentPlan> findByPatientIdAndStatusAndTenantId(UUID patientId, TreatmentPlanStatus status, UUID tenantId);

  /**
   * Lista todos los planes del tenant ordenados por fecha de creación descendente.
   */
  List<TreatmentPlan> findAllByTenantIdOrderByCreatedAtDesc(UUID tenantId);

  /**
   * Recupera un plan de tratamiento junto con sus ítems asociados en una sola consulta
   * ({@code LEFT JOIN FETCH}) para evitar el problema N+1.
   */
  @Query("""
      SELECT tp FROM TreatmentPlan tp
      LEFT JOIN FETCH tp.items
      WHERE tp.id = :id AND tp.tenantId = :tenantId
  """)
  Optional<TreatmentPlan> findWithItemsByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

  /**
   * Encuentra planes de tratamiento candidatos a la regla "tratamiento sin seguimiento"
   * (FASE9-01, insumo del motor de oportunidades).
   *
   * <p>Candidatos: planes en los estados indicados (típicamente {@code presentado} y
   * {@code en_decision}) cuya última interacción conocida sea anterior al {@code cutoff}.
   * La "última interacción" se resuelve con {@code COALESCE}: {@code lastContactAt} si
   * existe, luego {@code presentedAt}, luego {@code createdAt}.
   *
   * <p>Esta query se ejecuta desde un job de sistema (sin request HTTP → {@code ROOT_TENANT_ID}
   * sin filtro de tenant) para cubrir todas las clínicas en una sola corrida, igual que
   * {@code UnconfirmedAppointmentJob} de FASE8-02 y {@code OverdueInstallmentsJob} de FASE6-03.
   * Cada oportunidad generada hereda el {@code tenantId} del plan, manteniendo el aislamiento.
   *
   * @param statuses estados elegibles (ej. {@code presentado}, {@code en_decision}).
   * @param cutoff   fecha de corte: planes sin contacto posterior a esta fecha son candidatos.
   * @return lista de planes candidatos.
   */
  @Query("""
      SELECT tp FROM TreatmentPlan tp
      WHERE tp.status IN :statuses
        AND COALESCE(tp.lastContactAt, tp.presentedAt, tp.createdAt) < :cutoff
  """)
  List<TreatmentPlan> findFollowupCandidates(
      @Param("statuses") Collection<TreatmentPlanStatus> statuses,
      @Param("cutoff") Instant cutoff);
}

