package com.julio.odentix.odentix_backend.crm.dto;

import com.julio.odentix.odentix_backend.patient.dto.CreatePatientRequest;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/**
 * Datos complementarios u opcionales para la creación del paciente a partir de un lead (FASE5-03).
 *
 * <p>Si {@code firstName} o {@code lastName} son nulos o están vacíos, se inferirán
 * automáticamente a partir del {@code fullName} del prospecto.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
public class ConvertLeadPatientData {

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

  @Pattern(regexp = CreatePatientRequest.PHONE_REGEXP, message = "El formato de teléfono de emergencia es inválido")
  private String emergencyContactPhone;

  public ConvertLeadPatientData() {
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
