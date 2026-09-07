package com.julio.odentix.odentix_backend.patient.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * Entrada para crear un paciente (FASE2-02).
 *
 * <p>Nombres obligatorios; todo lo demás opcional. El tenant nunca viene
 * en el request: se toma del {@code TenantContext} del usuario autenticado
 * (si viniera del cliente, un tenant podría crear datos a nombre de otro).
 *
 * <p>Convención: clase explícita sin Lombok, como {@code LoginRequest}.
 */
public class CreatePatientRequest {

  // Teléfonos colombianos reales: +57 300..., espacios, guiones o
  // paréntesis. Flexible a propósito: valida formato, no existencia.
  public static final String PHONE_REGEXP = "^[+]?[0-9()\\- ]{7,20}$";

  @NotBlank(message = "El nombre no puede estar vacío")
  private String firstName;

  @NotBlank(message = "El apellido no puede estar vacío")
  private String lastName;

  private String documentType;

  private String documentNumber;

  private LocalDate birthDate;

  @Pattern(regexp = PHONE_REGEXP, message = "El formato de teléfono es inválido")
  private String phone;

  @Email(message = "El formato de email es inválido")
  private String email;

  private String address;

  private String emergencyContactName;

  @Pattern(regexp = PHONE_REGEXP, message = "El formato de teléfono es inválido")
  private String emergencyContactPhone;

  public CreatePatientRequest() {
  }

  public String getFirstName() {
    return firstName;
  }

  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public String getDocumentType() {
    return documentType;
  }

  public void setDocumentType(String documentType) {
    this.documentType = documentType;
  }

  public String getDocumentNumber() {
    return documentNumber;
  }

  public void setDocumentNumber(String documentNumber) {
    this.documentNumber = documentNumber;
  }

  public LocalDate getBirthDate() {
    return birthDate;
  }

  public void setBirthDate(LocalDate birthDate) {
    this.birthDate = birthDate;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getEmergencyContactName() {
    return emergencyContactName;
  }

  public void setEmergencyContactName(String emergencyContactName) {
    this.emergencyContactName = emergencyContactName;
  }

  public String getEmergencyContactPhone() {
    return emergencyContactPhone;
  }

  public void setEmergencyContactPhone(String emergencyContactPhone) {
    this.emergencyContactPhone = emergencyContactPhone;
  }
}
