package com.julio.odentix.odentix_backend.patient.dto;

import com.julio.odentix.odentix_backend.patient.entity.Patient;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Salida de paciente (FASE2-02). La entidad JPA nunca sale por la API:
 * el controlador siempre convierte con {@link #fromEntity(Patient)}.
 */
public class PatientResponse {

  private UUID id;
  private UUID tenantId;
  private String documentType;
  private String documentNumber;
  private String firstName;
  private String lastName;
  private LocalDate birthDate;
  private String phone;
  private String email;
  private String address;
  private String emergencyContactName;
  private String emergencyContactPhone;
  private boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;

  public PatientResponse() {
  }

  public PatientResponse(
      UUID id,
      UUID tenantId,
      String documentType,
      String documentNumber,
      String firstName,
      String lastName,
      LocalDate birthDate,
      String phone,
      String email,
      String address,
      String emergencyContactName,
      String emergencyContactPhone,
      boolean isActive,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.documentType = documentType;
    this.documentNumber = documentNumber;
    this.firstName = firstName;
    this.lastName = lastName;
    this.birthDate = birthDate;
    this.phone = phone;
    this.email = email;
    this.address = address;
    this.emergencyContactName = emergencyContactName;
    this.emergencyContactPhone = emergencyContactPhone;
    this.isActive = isActive;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static PatientResponse fromEntity(Patient patient) {
    return new PatientResponse(
        patient.getId(),
        patient.getTenantId(),
        patient.getDocumentType(),
        patient.getDocumentNumber(),
        patient.getFirstName(),
        patient.getLastName(),
        patient.getBirthDate(),
        patient.getPhone(),
        patient.getEmail(),
        patient.getAddress(),
        patient.getEmergencyContactName(),
        patient.getEmergencyContactPhone(),
        patient.isActive(),
        patient.getCreatedAt(),
        patient.getUpdatedAt()
    );
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

  public boolean isActive() {
    return isActive;
  }

  public void setActive(boolean active) {
    isActive = active;
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
}
