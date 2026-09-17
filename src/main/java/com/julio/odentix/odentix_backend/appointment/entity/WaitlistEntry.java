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
 * <p>Convención: Sin Lombok (código explícito según regla §9 de AGENTS.md).
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

  @Override
  public String toString() {
    return "WaitlistEntry{"
        + "id=" + getId()
        + ", patientId=" + (patient != null ? patient.getId() : "null")
        + ", status=" + status
        + '}';
  }
}
