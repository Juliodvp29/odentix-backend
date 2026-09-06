package com.julio.odentix.odentix_backend.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Entidad de prueba que hereda de {@link TenantAwareEntity} (FASE1-09, FASE1-10).
 * Representa una entidad de negocio con tabla real en Postgres para validar
 * el filtrado automático de tenant en repositorios.
 */
@Entity
@Table(name = "test_business_entities")
public class TestBusinessEntity extends TenantAwareEntity {

  @Column(name = "name", nullable = false)
  private String name;

  public TestBusinessEntity() {
    super();
  }

  public TestBusinessEntity(String name) {
    super();
    this.name = name;
  }

  public TestBusinessEntity(UUID tenantId, String name) {
    super(tenantId);
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
