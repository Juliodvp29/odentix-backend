package com.julio.odentix.odentix_backend.appointment.entity;

import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Entidad central de la agenda: cita odontológica (FASE3-02).
 *
 * <p>Representa una cita médica entre un paciente, un profesional y opcionalmente
 * un consultorio en un rango de tiempo determinado.
 *
 * <p>El no-solapamiento de citas para un mismo profesional se garantiza en
 * PostgreSQL mediante la restricción {@code no_overlapping_appointments}
 * ({@code EXCLUDE USING gist}).
 *
 * <p>Hereda de {@link TenantAwareEntity}, asegurando aislamiento multi-tenant
 * automático por {@code tenant_id}.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "appointments")
public class Appointment extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "professional_id")
  private Professional professional;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "room_id")
  private Room room;

  @Column(name = "procedure_id")
  private UUID procedureId;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(name = "estimated_value_cop", precision = 12, scale = 2)
  private BigDecimal estimatedValueCop = BigDecimal.ZERO;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "risk_level", nullable = false)
  private RiskLevel riskLevel = RiskLevel.bajo;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private AppointmentStatus status = AppointmentStatus.programada;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  public Appointment() {
    super();
  }

  public Appointment(
      UUID tenantId,
      Patient patient,
      Professional professional,
      Instant startsAt,
      Instant endsAt) {
    super(tenantId);
    this.patient = patient;
    this.professional = professional;
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    this.status = AppointmentStatus.programada;
    this.riskLevel = RiskLevel.bajo;
    this.estimatedValueCop = BigDecimal.ZERO;
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public Professional getProfessional() {
    return professional;
  }

  public void setProfessional(Professional professional) {
    this.professional = professional;
  }

  public Room getRoom() {
    return room;
  }

  public void setRoom(Room room) {
    this.room = room;
  }

  public UUID getProcedureId() {
    return procedureId;
  }

  public void setProcedureId(UUID procedureId) {
    this.procedureId = procedureId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(Instant startsAt) {
    this.startsAt = startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(Instant endsAt) {
    this.endsAt = endsAt;
  }

  public BigDecimal getEstimatedValueCop() {
    return estimatedValueCop;
  }

  public void setEstimatedValueCop(BigDecimal estimatedValueCop) {
    this.estimatedValueCop = estimatedValueCop != null ? estimatedValueCop : BigDecimal.ZERO;
  }

  public RiskLevel getRiskLevel() {
    return riskLevel;
  }

  public void setRiskLevel(RiskLevel riskLevel) {
    this.riskLevel = riskLevel != null ? riskLevel : RiskLevel.bajo;
  }

  public AppointmentStatus getStatus() {
    return status;
  }

  public void setStatus(AppointmentStatus status) {
    this.status = status != null ? status : AppointmentStatus.programada;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }
}

