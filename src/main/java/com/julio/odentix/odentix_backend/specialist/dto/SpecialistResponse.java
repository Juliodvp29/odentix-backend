package com.julio.odentix.odentix_backend.specialist.dto;

import com.julio.odentix.odentix_backend.specialist.entity.Specialist;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Especialista externo con sus condiciones financieras.
 *
 * <p>El nombre y la especialidad salen del {@code Professional} vinculado;
 * la entidad JPA nunca sale por la API.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class SpecialistResponse {

  private UUID id;
  private UUID professionalId;
  private String fullName;
  private String specialty;
  private BigDecimal feePercentage;
  private String paymentTerms;

  public SpecialistResponse() {
  }

  /**
   * Construye el DTO desde la entidad con su profesional ya cargado.
   *
   * <p>Debe llamarse dentro de una transacción de lectura porque el
   * profesional se carga de forma diferida.
   */
  public static SpecialistResponse fromEntity(Specialist specialist) {
    SpecialistResponse r = new SpecialistResponse();
    r.id = specialist.getId();
    r.professionalId = specialist.getProfessional().getId();
    r.fullName = specialist.getProfessional().getFullName();
    r.specialty = specialist.getProfessional().getSpecialty();
    r.feePercentage = specialist.getFeePercentage();
    r.paymentTerms = specialist.getPaymentTerms();
    return r;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getProfessionalId() {
    return professionalId;
  }

  public void setProfessionalId(UUID professionalId) {
    this.professionalId = professionalId;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public String getSpecialty() {
    return specialty;
  }

  public void setSpecialty(String specialty) {
    this.specialty = specialty;
  }

  public BigDecimal getFeePercentage() {
    return feePercentage;
  }

  public void setFeePercentage(BigDecimal feePercentage) {
    this.feePercentage = feePercentage;
  }

  public String getPaymentTerms() {
    return paymentTerms;
  }

  public void setPaymentTerms(String paymentTerms) {
    this.paymentTerms = paymentTerms;
  }
}
