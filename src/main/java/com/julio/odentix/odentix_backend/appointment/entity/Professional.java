package com.julio.odentix.odentix_backend.appointment.entity;

import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Profesional o facultativo de la clínica odontológica (FASE3-01).
 *
 * <p>Base de la agenda: modela a quien presta la atención clínica. Puede estar
 * vinculado opcionalmente a un {@link User} del sistema (odontólogos y
 * propietarios de planta), o carecer de usuario (especialistas externos que no
 * acceden a la aplicación).
 *
 * <p>Hereda de {@link TenantAwareEntity}, asegurando aislamiento automático
 * por {@code tenant_id}.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "professionals")
public class Professional extends TenantAwareEntity {

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "specialty")
  private String specialty;

  @Column(name = "license_number")
  private String licenseNumber;

  @Column(name = "is_external", nullable = false)
  private boolean isExternal = false;

  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  public Professional() {
    super();
  }

  public Professional(UUID tenantId, String fullName) {
    super(tenantId);
    this.fullName = fullName;
    this.isExternal = false;
    this.isActive = true;
  }

  public Professional(UUID tenantId, String fullName, String specialty, String licenseNumber) {
    super(tenantId);
    this.fullName = fullName;
    this.specialty = specialty;
    this.licenseNumber = licenseNumber;
    this.isExternal = false;
    this.isActive = true;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getSpecialty() {
    return specialty;
  }

  public void setSpecialty(String specialty) {
    this.specialty = specialty;
  }

  public String getLicenseNumber() {
    return licenseNumber;
  }

  public void setLicenseNumber(String licenseNumber) {
    this.licenseNumber = licenseNumber;
  }

  public boolean isExternal() {
    return isExternal;
  }

  public void setExternal(boolean external) {
    isExternal = external;
  }

  public boolean isActive() {
    return isActive;
  }

  public void setActive(boolean active) {
    isActive = active;
  }
}

