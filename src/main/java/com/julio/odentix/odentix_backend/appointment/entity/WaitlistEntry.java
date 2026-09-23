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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Interesado en lista de espera: paciente que quiere un horario o tipo de
 * procedimiento (FASE3-06).
 *
 * <p>Hereda de {@link TenantAwareEntity}, asegurando aislamiento multi-tenant
 * automático por {@code tenant_id}.
 *
 * <p>{@code procedureId} es un UUID suelto sin FK hasta que exista el
 * catálogo de procedimientos (mismo precedente que
 * {@code Appointment.procedureId} en FASE3-02).
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "waitlist_entries")
public class WaitlistEntry extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "patient_id", nullable = false)
  private Patient patient;

  @Column(name = "procedure_id")
  private UUID procedureId;

  @Column(name = "desired_from")
  private Instant desiredFrom;

  @Column(name = "desired_to")
  private Instant desiredTo;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private WaitlistStatus status = WaitlistStatus.activa;

  @Column(name = "contacted_at")
  private Instant contactedAt;

  @Column(name = "converted_at")
  private Instant convertedAt;

  @Column(name = "discarded_at")
  private Instant discardedAt;

  @Column(name = "discard_reason", columnDefinition = "TEXT")
  private String discardReason;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "converted_appointment_id")
  private Appointment convertedAppointment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "recovered_from_appointment_id")
  private Appointment recoveredFromAppointment;

  public WaitlistEntry() {
    super();
  }

  public WaitlistEntry(UUID tenantId, Patient patient) {
    super(tenantId);
    this.patient = patient;
    this.status = WaitlistStatus.activa;
  }

  public Patient getPatient() {
    return patient;
  }

  public void setPatient(Patient patient) {
    this.patient = patient;
  }

  public UUID getProcedureId() {
    return procedureId;
  }

  public void setProcedureId(UUID procedureId) {
    this.procedureId = procedureId;
  }

  public Instant getDesiredFrom() {
    return desiredFrom;
  }

  public void setDesiredFrom(Instant desiredFrom) {
    this.desiredFrom = desiredFrom;
  }

  public Instant getDesiredTo() {
    return desiredTo;
  }

  public void setDesiredTo(Instant desiredTo) {
    this.desiredTo = desiredTo;
  }

  public WaitlistStatus getStatus() {
    return status;
  }

  public void setStatus(WaitlistStatus status) {
    this.status = status != null ? status : WaitlistStatus.activa;
  }

  public Instant getContactedAt() {
    return contactedAt;
  }

  public void setContactedAt(Instant contactedAt) {
    this.contactedAt = contactedAt;
  }

  public Instant getConvertedAt() {
    return convertedAt;
  }

  public void setConvertedAt(Instant convertedAt) {
    this.convertedAt = convertedAt;
  }

  public Instant getDiscardedAt() {
    return discardedAt;
  }

  public void setDiscardedAt(Instant discardedAt) {
    this.discardedAt = discardedAt;
  }

  public String getDiscardReason() {
    return discardReason;
  }

  public void setDiscardReason(String discardReason) {
    this.discardReason = discardReason;
  }

  public Appointment getConvertedAppointment() {
    return convertedAppointment;
  }

  public void setConvertedAppointment(Appointment convertedAppointment) {
    this.convertedAppointment = convertedAppointment;
  }

  public Appointment getRecoveredFromAppointment() {
    return recoveredFromAppointment;
  }

  public void setRecoveredFromAppointment(Appointment recoveredFromAppointment) {
    this.recoveredFromAppointment = recoveredFromAppointment;
  }

  @Override
  public String toString() {
    return "WaitlistEntry{"
        + "id=" + getId()
        + ", patientId=" + (patient != null ? patient.getId() : "null")
        + ", status=" + status
        + '}';
  }
}

