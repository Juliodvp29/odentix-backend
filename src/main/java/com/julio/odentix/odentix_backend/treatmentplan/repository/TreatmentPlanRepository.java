package com.julio.odentix.odentix_backend.treatmentplan.repository;

import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
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
   * de {@code @TenantId} (defensa en profundidad, regla §5 de AGENTS.md).
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
}
