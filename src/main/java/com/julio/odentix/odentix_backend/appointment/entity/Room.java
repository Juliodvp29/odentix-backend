package com.julio.odentix.odentix_backend.appointment.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/**
 * Consultorio o espacio físico de atención de la clínica (FASE3-01).
 *
 * <p>Permite asociar citas a un consultorio específico en la agenda.
 * El nombre es único por clínica/tenant.
 *
 * <p>Hereda de {@link TenantAwareEntity}, asegurando aislamiento automático
 * por {@code tenant_id}.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(
    name = "rooms",
    uniqueConstraints = {
        @UniqueConstraint(name = "rooms_tenant_id_name_key", columnNames = {"tenant_id", "name"})
    }
)
public class Room extends TenantAwareEntity {

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  public Room() {
    super();
  }

  public Room(UUID tenantId, String name) {
    super(tenantId);
    this.name = name;
    this.isActive = true;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isActive() {
    return isActive;
  }

  public void setActive(boolean active) {
    isActive = active;
  }
}

