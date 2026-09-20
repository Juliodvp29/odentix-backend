package com.julio.odentix.odentix_backend.patient.entity;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Entrada de odontograma de un paciente (FASE2-07).
 *
 * <p>Cada fila es un momento clínico distinto: varias entradas pueden
 * referirse a la misma pieza dental sin que una sobreescriba a la otra.
 * Hereda de {@link TenantAwareEntity} para aislamiento automático por
 * tenant ({@code @TenantId}, FASE1-09).
 *
 * <p>{@code recordedBy} se almacena como UUID suelto por ahora: la tabla
 * {@code professionals} se crea en Fase 3 (FASE3-01), momento en el que
 * se añadirá la relación JPA correspondiente (mismo precedente que
 * {@code ClinicalRecord.professionalId} en FASE2-05).
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "odontogram_entries")
public class OdontogramEntry extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "patient_id", nullable = false, updatable = false)
  private Patient patient;

  // Pieza dental en notación FDI (11–48). El rango lo garantiza el CHECK
  // de la migración V8; la validación de entrada vivirá en los DTOs de
  // FASE2-08 (las entidades no llevan Bean Validation en este proyecto).
  @Column(name = "tooth_number", nullable = false, updatable = false)
  private short toothNumber;

  @Column(name = "surface")
  private String surface;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "entry_type", nullable = false, updatable = false)
  private OdontogramEntryType entryType;

  @Column(name = "condition", nullable = false)
  private String condition;

  // UUID suelto hasta que exista la tabla professionals (Fase 3).
  @Column(name = "recorded_by")
  private UUID recordedBy;

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  public OdontogramEntry() {
    super();
  }

  public OdontogramEntry(UUID tenantId, Patient patient, short toothNumber,
      OdontogramEntryType entryType, String condition) {
    super(tenantId);
    this.patient = patient;
    this.toothNumber = toothNumber;
    this.entryType = entryType;
    this.condition = condition;
    this.recordedAt = Instant.now();
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public short getToothNumber() {
    return toothNumber;
  }

  public void setToothNumber(short toothNumber) {
    this.toothNumber = toothNumber;
  }

  public String getSurface() {
    return surface;
  }

  public void setSurface(String surface) {
    this.surface = surface;
  }

  public OdontogramEntryType getEntryType() {
    return entryType;
  }

  public void setEntryType(OdontogramEntryType entryType) {
    this.entryType = entryType;
  }

  public String getCondition() {
    return condition;
  }

  public void setCondition(String condition) {
    this.condition = condition;
  }

  public UUID getRecordedBy() {
    return recordedBy;
  }

  public void setRecordedBy(UUID recordedBy) {
    this.recordedBy = recordedBy;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }

  public void setRecordedAt(Instant recordedAt) {
    this.recordedAt = recordedAt;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  @Override
  public String toString() {
    // Sin condición ni notas en logs (datos clínicos del tenant).
    return "OdontogramEntry{"
        + "id=" + getId()
        + ", patientId=" + (patient != null ? patient.getId() : "null")
        + ", toothNumber=" + toothNumber
        + ", entryType=" + entryType
        + '}';
  }
}

