package com.julio.odentix.odentix_backend.patient.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Registro de historia clínica de un paciente (FASE2-05).
 *
 * <p>Versión reducida: motivo de consulta, antecedentes, diagnóstico y
 * evolución. Hereda de {@link TenantAwareEntity} para aislamiento
 * automático por tenant ({@code @TenantId}, FASE1-09).
 *
 * <p>{@code recordedAt} es la fecha clínica del registro (puede
 * diferir de {@code createdAt} para permitir registrar entradas
 * clínicas retroactivas).
 *
 * <p>{@code professionalId} se almacena como UUID suelto por ahora:
 * la tabla {@code professionals} se crea en Fase 3 (FASE3-01), momento
 * en el que se añadirá la relación JPA correspondiente.
 *
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
 */
@Entity
@Table(name = "clinical_records")
public class ClinicalRecord extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "patient_id", nullable = false, updatable = false)
  private Patient patient;

  // UUID suelto hasta que exista la tabla professionals (Fase 3).
  @Column(name = "professional_id")
  private UUID professionalId;

  @Column(name = "chief_complaint", columnDefinition = "TEXT")
  private String chiefComplaint;

  @Column(name = "anamnesis", columnDefinition = "TEXT")
  private String anamnesis;

  @Column(name = "diagnosis", columnDefinition = "TEXT")
  private String diagnosis;

  @Column(name = "evolution", columnDefinition = "TEXT")
  private String evolution;

  // Fecha clínica del registro — separada de createdAt para permitir
  // registrar entradas retroactivas sin alterar la auditoría de creación.
  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  public ClinicalRecord() {
    super();
  }

  public ClinicalRecord(UUID tenantId, Patient patient) {
    super(tenantId);
    this.patient = patient;
    this.recordedAt = Instant.now();
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public UUID getProfessionalId() {
    return professionalId;
  }

  public void setProfessionalId(UUID professionalId) {
    this.professionalId = professionalId;
  }

  public String getChiefComplaint() {
    return chiefComplaint;
  }

  public void setChiefComplaint(String chiefComplaint) {
    this.chiefComplaint = chiefComplaint;
  }

  public String getAnamnesis() {
    return anamnesis;
  }

  public void setAnamnesis(String anamnesis) {
    this.anamnesis = anamnesis;
  }

  public String getDiagnosis() {
    return diagnosis;
  }

  public void setDiagnosis(String diagnosis) {
    this.diagnosis = diagnosis;
  }

  public String getEvolution() {
    return evolution;
  }

  public void setEvolution(String evolution) {
    this.evolution = evolution;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public void setRecordedAt(Instant recordedAt) {
    this.recordedAt = recordedAt;
  }

  @Override
  public String toString() {
    // Sin datos clínicos en logs (PII/PHI del tenant, regla §5.5 de AGENTS.md).
    return "ClinicalRecord{"
        + "id=" + getId()
        + ", patientId=" + (patient != null ? patient.getId() : "null")
        + '}';
  }
}
