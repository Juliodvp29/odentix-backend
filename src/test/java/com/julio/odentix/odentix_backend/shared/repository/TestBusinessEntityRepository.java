package com.julio.odentix.odentix_backend.shared.repository;

import com.julio.odentix.odentix_backend.shared.entity.TestBusinessEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repositorio de prueba con consultas "ingenuas" (sin cláusula tenant_id manual)
 * para comprobar que Hibernate inyecta automáticamente el filtro (FASE1-09).
 */
public interface TestBusinessEntityRepository extends JpaRepository<TestBusinessEntity, UUID> {

  List<TestBusinessEntity> findByName(String name);

  @Query("SELECT t FROM TestBusinessEntity t WHERE t.name = :name")
  List<TestBusinessEntity> findByNameJpql(@Param("name") String name);
}
