package com.julio.odentix.odentix_backend.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import java.util.UUID;

/**
 * Profesional de la clínica para selectores (agenda) y administración.
 *
 * <p>Los booleanos se exponen como {@code isExternal}/{@code isActive} por
 * contrato con el front; se logra con {@code @JsonProperty} porque sin ella
 * Jackson serializaría {@code external}/{@code active} (ver PatientResponse).
 *
 * <p>La entidad JPA nunca sale por la API.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class ProfessionalResponse {

  private UUID id;
  private String fullName;
  private String specialty;
  private String licenseNumber;
  private boolean external;
  private boolean active;

  public ProfessionalResponse() {
  }

  /**
   * Construye el DTO desde la entidad.
   */
  public static ProfessionalResponse fromEntity(Professional profesional) {
    ProfessionalResponse r = new ProfessionalResponse();
    r.id = profesional.getId();
    r.fullName = profesional.getFullName();
    r.specialty = profesional.getSpecialty();
    r.licenseNumber = profesional.getLicenseNumber();
    r.external = profesional.isExternal();
    r.active = profesional.isActive();
    return r;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
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

  @JsonProperty("isExternal")
  public boolean isExternal() {
    return external;
  }

  public void setExternal(boolean external) {
    this.external = external;
  }

  @JsonProperty("isActive")
  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
