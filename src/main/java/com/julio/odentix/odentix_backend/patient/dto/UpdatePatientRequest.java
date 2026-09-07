package com.julio.odentix.odentix_backend.patient.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * Entrada para actualización parcial de un paciente (PATCH, FASE2-02).
 *
 * <p>Todos los campos son anulables: {@code null} significa "no cambiar",
 * sin {@code @NotBlank} (un nombre ausente no es un error en un PATCH).
 * Las reglas de formato sí aplican cuando el campo viene presente:
 * {@code ""} o un email/teléfono malformado devuelven 400 igual que en POST.
 */
public class UpdatePatientRequest {

  private String firstName;

  private String lastName;

  private String documentType;

  private String documentNumber;

  private LocalDate birthDate;

  @Pattern(regexp = CreatePatientRequest.PHONE_REGEXP, message = "El formato de teléfono es inválido")
  private String phone;

  @Email(message = "El formato de email es inválido")
  private String email;

  private String address;

  private String emergencyContactName;

  @Pattern(regexp = CreatePatientRequest.PHONE_REGEXP, message = "El formato de teléfono es inválido")
  private String emergencyContactPhone;

  public UpdatePatientRequest() {
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
