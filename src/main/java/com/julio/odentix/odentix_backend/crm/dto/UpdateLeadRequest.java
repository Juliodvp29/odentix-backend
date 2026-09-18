package com.julio.odentix.odentix_backend.crm.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Solicitud de actualización de información general de un prospecto (FASE5-02).
 */
public class UpdateLeadRequest {

  @NotBlank(message = "El nombre completo es obligatorio")
  private String fullName;

  private String phone;

  @Email(message = "El formato de correo electrónico no es válido")
  private String email;

  private String source;

  private String campaign;

  private String procedureOfInterest;

  @DecimalMin(value = "0.00", message = "El valor estimado no puede ser negativo")
  private BigDecimal estimatedValueCop;

  private UUID assignedToId;

  private Instant nextActionAt;

  public UpdateLeadRequest() {
  }

  public UpdateLeadRequest(String fullName, String phone, String email, String source) {
    this.fullName = fullName;
    this.phone = phone;
    this.email = email;
    this.source = source;
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

  public UUID getAssignedToId() {
    return assignedToId;
  }

  public void setAssignedToId(UUID assignedToId) {
    this.assignedToId = assignedToId;
  }

  public Instant getNextActionAt() {
    return nextActionAt;
  }

  public void setNextActionAt(Instant nextActionAt) {
    this.nextActionAt = nextActionAt;
  }
}
