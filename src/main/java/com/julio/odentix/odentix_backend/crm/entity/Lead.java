package com.julio.odentix.odentix_backend.crm.entity;

import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType;

/**
 * Prospecto o contacto comercial en el CRM de la clínica (FASE5-01).
 *
 * <p>Modela el embudo de ventas para captación de nuevos pacientes, registrando
 * canal de origen, campaña de marketing, procedimiento deseado y valor estimado,
 * además de la trazabilidad temporal del contacto.
 *
 * <p>Hereda de {@link TenantAwareEntity}, asegurando aislamiento automático
 * por {@code tenant_id}.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "leads")
public class Lead extends TenantAwareEntity {

  @Column(name = "full_name", nullable = false)
  private String fullName;

  @Column(name = "phone")
  private String phone;

  @Column(name = "email", columnDefinition = "citext")
  private String email;

  @Column(name = "source")
  private String source;

  @Column(name = "campaign")
  private String campaign;

  @Column(name = "procedure_of_interest")
  private String procedureOfInterest;

  @Column(name = "estimated_value_cop", precision = 12, scale = 2)
  private BigDecimal estimatedValueCop;

  @Enumerated(EnumType.STRING)
  @JdbcType(PostgreSQLEnumJdbcType.class)
  @Column(name = "status", nullable = false)
  private LeadStatus status = LeadStatus.nuevo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assigned_to")
  private User assignedTo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "converted_patient_id")
  private Patient convertedPatient;

  @Column(name = "last_contact_at")
  private Instant lastContactAt;

  @Column(name = "next_action_at")
  private Instant nextActionAt;

  @OneToMany(mappedBy = "lead", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<LeadActivity> activities = new ArrayList<>();

  public Lead() {
  }

  public Lead(UUID tenantId, String fullName, String phone, String email, String source) {
    super(tenantId);
    this.fullName = fullName;
    this.phone = phone;
    this.email = email;
    this.source = source;
  }

  public Lead(String fullName, String phone, String email, String source) {
    this(null, fullName, phone, email, source);
  }

  public void addActivity(LeadActivity activity) {
    activities.add(activity);
    activity.setLead(this);
    if (this.getTenantId() != null) {
      activity.setTenantId(this.getTenantId());
    }
  }

  public void removeActivity(LeadActivity activity) {
    activities.remove(activity);
    activity.setLead(null);
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
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

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getCampaign() {
    return campaign;
  }

  public void setCampaign(String campaign) {
    this.campaign = campaign;
  }

  public String getProcedureOfInterest() {
    return procedureOfInterest;
  }

  public void setProcedureOfInterest(String procedureOfInterest) {
    this.procedureOfInterest = procedureOfInterest;
  }

  public BigDecimal getEstimatedValueCop() {
    return estimatedValueCop;
  }

  public void setEstimatedValueCop(BigDecimal estimatedValueCop) {
    this.estimatedValueCop = estimatedValueCop;
  }

  public LeadStatus getStatus() {
    return status;
  }

  public void setStatus(LeadStatus status) {
    this.status = status;
  }

  public User getAssignedTo() {
    return assignedTo;
  }

  public void setAssignedTo(User assignedTo) {
    this.assignedTo = assignedTo;
  }

  public Patient getConvertedPatient() {
    return convertedPatient;
  }

  public void setConvertedPatient(Patient convertedPatient) {
    this.convertedPatient = convertedPatient;
  }

  public Instant getLastContactAt() {
    return lastContactAt;
  }

  public void setLastContactAt(Instant lastContactAt) {
    this.lastContactAt = lastContactAt;
  }

  public Instant getNextActionAt() {
    return nextActionAt;
  }

  public void setNextActionAt(Instant nextActionAt) {
    this.nextActionAt = nextActionAt;
  }

  public List<LeadActivity> getActivities() {
    return activities;
  }

  public void setActivities(List<LeadActivity> activities) {
    this.activities = activities;
  }
}

