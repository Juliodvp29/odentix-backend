package com.julio.odentix.odentix_backend.crm.entity;

import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Registro de actividad o interacción de contacto con un prospecto (FASE5-01).
 *
 * <p>Representa una llamada, mensaje de WhatsApp, correo electrónico o nota
 * interna registrada por un usuario respecto a un lead comercial.
 *
 * <p>Hereda de {@link TenantAwareEntity}, asegurando aislamiento automático
 * por {@code tenant_id}.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
@Entity
@Table(name = "lead_activities")
public class LeadActivity extends TenantAwareEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "lead_id", nullable = false)
  private Lead lead;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id")
  private User user;

  @Enumerated(EnumType.STRING)
  @Column(name = "activity_type", nullable = false)
  private LeadActivityType activityType;

  @Column(name = "notes", columnDefinition = "TEXT")
  private String notes;

  public LeadActivity() {
  }

  public LeadActivity(UUID tenantId, Lead lead, User user, LeadActivityType activityType, String notes) {
    super(tenantId);
    this.lead = lead;
    this.user = user;
    this.activityType = activityType;
    this.notes = notes;
  }

  public LeadActivity(Lead lead, User user, LeadActivityType activityType, String notes) {
    this(null, lead, user, activityType, notes);
  }

  public Lead getLead() {
    return lead;
  }

  public void setLead(Lead lead) {
    this.lead = lead;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public LeadActivityType getActivityType() {
    return activityType;
  }

  public void setActivityType(LeadActivityType activityType) {
    this.activityType = activityType;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }
}

