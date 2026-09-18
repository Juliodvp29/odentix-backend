package com.julio.odentix.odentix_backend.treatmentplan.dto;

import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Representación pública de un plan de tratamiento odontológico y sus ítems (FASE4-02).
 */
public class TreatmentPlanResponse {

  private UUID id;
  private UUID patientId;
  private String patientFullName;
  private UUID professionalId;
  private String professionalFullName;
  private String diagnosis;
  private TreatmentPlanStatus status;
  private BigDecimal totalPriceCop;
  private Instant presentedAt;
  private Instant lastContactAt;
  private List<TreatmentPlanItemResponse> items = new ArrayList<>();
  private Instant createdAt;
  private Instant updatedAt;

  public TreatmentPlanResponse() {
  }

  public TreatmentPlanResponse(
      UUID id,
      UUID patientId,
      String patientFullName,
      UUID professionalId,
      String professionalFullName,
      String diagnosis,
      TreatmentPlanStatus status,
      BigDecimal totalPriceCop,
      Instant presentedAt,
      Instant lastContactAt,
      List<TreatmentPlanItemResponse> items,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.patientId = patientId;
    this.patientFullName = patientFullName;
    this.professionalId = professionalId;
    this.professionalFullName = professionalFullName;
    this.diagnosis = diagnosis;
    this.status = status;
    this.totalPriceCop = totalPriceCop;
    this.presentedAt = presentedAt;
    this.lastContactAt = lastContactAt;
    this.items = items != null ? items : new ArrayList<>();
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public static TreatmentPlanResponse fromEntity(TreatmentPlan plan) {
    if (plan == null) {
      return null;
    }

    String patientName = null;
    UUID patientId = null;
    if (plan.getPatient() != null) {
      patientId = plan.getPatient().getId();
      patientName = plan.getPatient().getFirstName() + " " + plan.getPatient().getLastName();
    }

    String professionalName = null;
    UUID profId = null;
    if (plan.getProfessional() != null) {
      profId = plan.getProfessional().getId();
      professionalName = plan.getProfessional().getFullName();
    }

    List<TreatmentPlanItemResponse> itemsResponse = new ArrayList<>();
    if (plan.getItems() != null) {
      for (var item : plan.getItems()) {
        itemsResponse.add(TreatmentPlanItemResponse.fromEntity(item));
      }
    }

    return new TreatmentPlanResponse(
        plan.getId(),
        patientId,
        patientName,
        profId,
        professionalName,
        plan.getDiagnosis(),
        plan.getStatus(),
        plan.getTotalPriceCop(),
        plan.getPresentedAt(),
        plan.getLastContactAt(),
        itemsResponse,
        plan.getCreatedAt(),
        plan.getUpdatedAt()
    );
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public String getPatientFullName() {
    return patientFullName;
  }

  public void setPatientFullName(String patientFullName) {
    this.patientFullName = patientFullName;
  }

  public UUID getProfessionalId() {
    return professionalId;
  }

  public void setProfessionalId(UUID professionalId) {
    this.professionalId = professionalId;
  }

  public String getProfessionalFullName() {
    return professionalFullName;
  }

  public void setProfessionalFullName(String professionalFullName) {
    this.professionalFullName = professionalFullName;
  }

  public String getDiagnosis() {
    return diagnosis;
  }

  public void setDiagnosis(String diagnosis) {
    this.diagnosis = diagnosis;
  }

  public TreatmentPlanStatus getStatus() {
    return status;
  }

  public void setStatus(TreatmentPlanStatus status) {
    this.status = status;
  }

  public BigDecimal getTotalPriceCop() {
    return totalPriceCop;
  }

  public void setTotalPriceCop(BigDecimal totalPriceCop) {
    this.totalPriceCop = totalPriceCop;
  }

  public Instant getPresentedAt() {
    return presentedAt;
  }

  public void setPresentedAt(Instant presentedAt) {
    this.presentedAt = presentedAt;
  }

  public Instant getLastContactAt() {
    return lastContactAt;
  }

  public void setLastContactAt(Instant lastContactAt) {
    this.lastContactAt = lastContactAt;
  }

  public List<TreatmentPlanItemResponse> getItems() {
    return items;
  }

  public void setItems(List<TreatmentPlanItemResponse> items) {
    this.items = items != null ? items : new ArrayList<>();
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
