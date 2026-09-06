package com.julio.odentix.odentix_backend.shared.entity;

import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.TenantId;

/**
 * Base para toda entidad de negocio (FASE1-04, FASE1-09).
 *
 * <p>Toda tabla de negocio (no catálogos globales) debe llevar {@code tenant_id}
 * desde su primera migración, y su entidad debe heredar de esta clase: así el
 * olvido del filtro por tenant se vuelve un error de diseño visible, no un
 * detalle a recordar en cada query (defensa en profundidad, regla §5 de AGENTS.md).
 *
 * <p>Anotado con {@link TenantId} para que Hibernate 6/7 aplique el filtro automático
 * de tenant tanto en queries masivas (findAll, JPQL, Criteria) como en búsquedas por
 * ID directo (findById / entityManager.find), resolviendo el tenant a través de
 * {@link com.julio.odentix.odentix_backend.shared.context.TenantIdentifierResolver}.
 *
 * <p>Si la entidad además necesita navegar a la clínica (ej. joins), la asociación
 * se mapea sobre la misma columna con {@code insertable = false, updatable = false}
 * para no duplicar el mapeo de {@code tenant_id}.
 *
 * <p>Excepciones intencionales (no heredan de aquí): {@code Tenant} —es la raíz,
 * no pertenece a ningún tenant— y {@code User} —ya modela el tenant vía asociación
 * y su repositorio filtra por tenant desde FASE1-02.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@MappedSuperclass
public abstract class TenantAwareEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @TenantId
  @Column(name = "tenant_id", nullable = false, updatable = false)
  private UUID tenantId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected TenantAwareEntity() {
  }

  protected TenantAwareEntity(UUID tenantId) {
    this.tenantId = tenantId;
  }

  @PrePersist
  protected void onCreate() {
    Instant now = Instant.now();
    if (this.createdAt == null) {
      this.createdAt = now;
    }
    if (this.updatedAt == null) {
      this.updatedAt = now;
    }
    if (this.tenantId == null) {
      this.tenantId = TenantContext.getTenantId();
    }
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getTenantId() {
    return tenantId;
  }

  public void setTenantId(UUID tenantId) {
    this.tenantId = tenantId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    TenantAwareEntity that = (TenantAwareEntity) o;
    return id != null && Objects.equals(id, that.id);
  }

  @Override
  public int hashCode() {
    return getClass().hashCode();
  }
}
