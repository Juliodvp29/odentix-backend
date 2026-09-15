package com.julio.odentix.odentix_backend.patient.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Paciente de la clínica (FASE2-01).
 *
 * <p>Primera entidad de negocio real del proyecto: hereda de
 * {@link TenantAwareEntity}, así el {@code tenant_id} se filtra y asigna
 * automáticamente (@TenantId, FASE1-09) sin configuración adicional.
 *
 * <p>Alcance reducido (sección 8.2 del doc de arquitectura): datos
 * personales, contacto y contacto de emergencia. Sin odontograma ni
 * historia clínica todavía (FASE2-05/07 con sus propias entidades).
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "patients")
public class Patient extends TenantAwareEntity {

  @Column(name = "document_type")
  private String documentType;

  @Column(name = "document_number")
  private String documentNumber;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "phone")
  private String phone;

  // CITEXT en BD (case-insensitive, como users.email): mismo email con
  // distinta capitalización es el mismo paciente para búsquedas.
  @Column(name = "email", columnDefinition = "citext")
  private String email;

  @Column(name = "address")
  private String address;

  @Column(name = "emergency_contact_name")
  private String emergencyContactName;

  @Column(name = "emergency_contact_phone")
  private String emergencyContactPhone;

  // Relación bidireccional con ClinicalRecord (FASE2-05): la historia
  // clínica del paciente se gestiona desde esta entidad vía cascada.
  @OneToMany(mappedBy = "patient", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<ClinicalRecord> clinicalRecords = new ArrayList<>();

  // Baja lógica (FASE2-02): DELETE marca false, nunca borra la fila.
  @Column(name = "is_active", nullable = false)
  private boolean isActive = true;

  public Patient() {
    super();
  }

  public Patient(UUID tenantId, String firstName, String lastName) {
    super(tenantId);
    this.firstName = firstName;
    this.lastName = lastName;
    this.isActive = true;
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

  public List<ClinicalRecord> getClinicalRecords() {
    return clinicalRecords;
  }

  public void setClinicalRecords(List<ClinicalRecord> clinicalRecords) {
    this.clinicalRecords = clinicalRecords;
  }

  public void addClinicalRecord(ClinicalRecord clinicalRecord) {
    this.clinicalRecords.add(clinicalRecord);
    clinicalRecord.setPatient(this);
  }

  @Override
  public String toString() {
    // Nunca incluir documento, teléfono, email ni dirección: son PII del
    // tenant (regla §5.5 de AGENTS.md: ni en logs de debug).
    return "Patient{"
        + "id=" + getId()
        + ", isActive=" + isActive
        + '}';
  }
}
