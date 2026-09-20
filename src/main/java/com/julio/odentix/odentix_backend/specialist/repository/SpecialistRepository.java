package com.julio.odentix.odentix_backend.specialist.repository;

import com.julio.odentix.odentix_backend.specialist.entity.Specialist;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para {@link Specialist} (FASE7-01).
 *
 * <p>Al heredar de {@link JpaRepository} sobre una
 * {@link com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity},
 * todas las operaciones quedan automáticamente filtradas por el {@code tenant_id}
 * activo en el contexto gracias a {@code @TenantId} de Hibernate 6.
 */
@Repository
public interface SpecialistRepository extends JpaRepository<Specialist, UUID> {

  /**
   * Busca un especialista por su ID y tenant (defensa en profundidad por tenant).
   */
  Optional<Specialist> findByIdAndTenantId(UUID id, UUID tenantId);

  /**
   * Busca el registro de especialista asociado a un profesional.
   *
   * @param professionalId identificador del profesional.
   * @return especialista asociado, si existe en el tenant activo.
   */
  Optional<Specialist> findByProfessionalId(UUID professionalId);
}

