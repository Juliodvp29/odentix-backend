package com.julio.odentix.odentix_backend.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Solicitud para crear un profesional de la clínica.
 *
 * <p>{@code isExternal} es opcional y por defecto {@code false} (personal de
 * planta); solo el propietario puede definirlo por su impacto en nómina.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class CreateProfessionalRequest {

  @NotBlank(message = "fullName es obligatorio")
  private String fullName;

  private String specialty;

  private String licenseNumber;

  private boolean external;

  public CreateProfessionalRequest() {
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
}
