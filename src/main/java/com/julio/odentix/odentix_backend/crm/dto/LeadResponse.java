package com.julio.odentix.odentix_backend.crm.dto;

import com.julio.odentix.odentix_backend.crm.entity.Lead;
import com.julio.odentix.odentix_backend.crm.entity.LeadStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Representación pública de un prospecto comercial (FASE5-02).
 */
public class LeadResponse {

  private UUID id;
  private UUID tenantId;
  private String fullName;
  private String phone;
  private String email;
  private String source;
  private String campaign;
  private String procedureOfInterest;
  private BigDecimal estimatedValueCop;
  private LeadStatus status;
  private UUID assignedToId;
  private String assignedToName;
  private UUID convertedPatientId;
  private String convertedPatientName;
  private Instant lastContactAt;
  private Instant nextActionAt;
  private Instant createdAt;
  private Instant updatedAt;

  public LeadResponse() {
  }

  public LeadResponse(
      UUID id,
      UUID tenantId,
      String fullName,
      String phone,
      String email,
      String source,
      String campaign,
      String procedureOfInterest,
      BigDecimal estimatedValueCop,
      LeadStatus status,
      UUID assignedToId,
      String assignedToName,
      UUID convertedPatientId,
      String convertedPatientName,
      Instant lastContactAt,
      Instant nextActionAt,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.tenantId = tenantId;
    this.fullName = fullName;
    this.phone = phone;
    this.email = email;
    this.source = source;
    this.campaign = campaign;
    this.procedureOfInterest = procedureOfInterest;
    this.estimatedValueCop = estimatedValueCop;
    this.status = status;
    this.assignedToId = assignedToId;
    this.assignedToName = assignedToName;
    this.convertedPatientId = convertedPatientId;
    this.convertedPatientName = convertedPatientName;
    this.lastContactAt = lastContactAt;
    this.nextActionAt = nextActionAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static LeadResponse fromEntity(Lead lead) {
    if (lead == null) {
      return null;
    }
    String patientFullName = null;
    if (lead.getConvertedPatient() != null) {
      patientFullName = lead.getConvertedPatient().getFirstName() + " " + lead.getConvertedPatient().getLastName();
    }
    return new LeadResponse(
        lead.getId(),
        lead.getTenantId(),
        lead.getFullName(),
        lead.getPhone(),
        lead.getEmail(),
        lead.getSource(),
        lead.getCampaign(),
        lead.getProcedureOfInterest(),
        lead.getEstimatedValueCop(),
        lead.getStatus(),
        lead.getAssignedTo() != null ? lead.getAssignedTo().getId() : null,
        lead.getAssignedTo() != null ? lead.getAssignedTo().getFullName() : null,
        lead.getConvertedPatient() != null ? lead.getConvertedPatient().getId() : null,
        patientFullName,
        lead.getLastContactAt(),
        lead.getNextActionAt(),
        lead.getCreatedAt(),
        lead.getUpdatedAt()
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

  public UUID getAssignedToId() {
    return assignedToId;
  }

  public void setAssignedToId(UUID assignedToId) {
    this.assignedToId = assignedToId;
  }

  public String getAssignedToName() {
    return assignedToName;
  }

  public void setAssignedToName(String assignedToName) {
    this.assignedToName = assignedToName;
  }

  public UUID getConvertedPatientId() {
    return convertedPatientId;
  }

  public void setConvertedPatientId(UUID convertedPatientId) {
    this.convertedPatientId = convertedPatientId;
  }

  public String getConvertedPatientName() {
    return convertedPatientName;
  }

  public void setConvertedPatientName(String convertedPatientName) {
    this.convertedPatientName = convertedPatientName;
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
