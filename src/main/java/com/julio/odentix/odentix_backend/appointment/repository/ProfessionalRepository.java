package com.julio.odentix.odentix_backend.appointment.repository;

import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad {@link Professional} (FASE3-01).
 *
 * <p>Al heredar de {@link JpaRepository} sobre una {@link com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity},
 * todas las operaciones quedan automáticamente filtradas por el {@code tenant_id}
 * activo en el contexto gracias a {@code @TenantId} de Hibernate 6.
 */
@Repository
public interface ProfessionalRepository extends JpaRepository<Professional, UUID> {

  /**
   * Busca un profesional por su ID y tenant (defensa en profundidad, regla §5.2 de AGENTS.md).
   */
  Optional<Professional> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Busca un profesional vinculado a un usuario específico por su ID.
   *
   * @param userId identificador del usuario del sistema.
   * @return profesional asociado, si existe.
   */
  Optional<Professional> findByUserId(UUID userId);

  /**
   * Retorna todos los profesionales activos del tenant.
   *
   * @return lista de profesionales activos.
   */
  List<Professional> findByIsActiveTrue();

  /**
   * Retorna los profesionales filtrando por si son externos o de planta.
   *
   * @param isExternal true para especialistas externos, false para planta.
   * @return lista de profesionales correspondientes.
   */
  List<Professional> findByIsExternal(boolean isExternal);

  /**
   * Determina si ya existe un profesional vinculado al usuario dado.
   *
   * @param userId identificador del usuario.
   * @return true si ya existe un profesional con dicho usuario.
   */
  boolean existsByUserId(UUID userId);
}
