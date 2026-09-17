package com.julio.odentix.odentix_backend.appointment.repository;

import com.julio.odentix.odentix_backend.appointment.entity.Room;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repositorio Spring Data JPA para la entidad {@link Room} (FASE3-01).
 *
 * <p>Al heredar de {@link JpaRepository} sobre una {@link com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity},
 * todas las operaciones quedan automáticamente filtradas por el {@code tenant_id}
 * activo en el contexto gracias a {@code @TenantId} de Hibernate 6.
 */
@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {

  /**
   * Busca un consultorio por su nombre (sin distinguir mayúsculas/minúsculas).
   *
   * @param name nombre del consultorio.
   * @return consultorio encontrado, si existe en el tenant actual.
   */
  Optional<Room> findByNameIgnoreCase(String name);

  /**
   * Retorna todos los consultorios activos del tenant.
   *
   * @return lista de consultorios activos.
   */
  List<Room> findByIsActiveTrue();

  /**
   * Verifica si ya existe un consultorio con el mismo nombre en el tenant actual.
   *
   * @param name nombre a verificar.
   * @return true si ya existe.
   */
  boolean existsByNameIgnoreCase(String name);
}
