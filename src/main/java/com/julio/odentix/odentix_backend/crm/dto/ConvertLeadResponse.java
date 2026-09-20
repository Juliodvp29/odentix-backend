package com.julio.odentix.odentix_backend.crm.dto;

import com.julio.odentix.odentix_backend.appointment.dto.AppointmentResponse;
import com.julio.odentix.odentix_backend.patient.dto.PatientResponse;
import java.util.UUID;

/**
 * Respuesta a la conversión de un prospecto comercial (Lead) a paciente (FASE5-03).
 *
 * <p>Si el prospecto ya había sido convertido previamente a un paciente, se retorna
 * la información existente con {@code alreadyConverted = true}, garantizando idempotencia.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class ConvertLeadResponse {

  private UUID leadId;
  private UUID patientId;
  private PatientResponse patient;
  private UUID appointmentId;
  private AppointmentResponse appointment;
  private boolean alreadyConverted;

  public ConvertLeadResponse() {
  }

  public ConvertLeadResponse(
      UUID leadId,
      UUID patientId,
      PatientResponse patient,
      UUID appointmentId,
      AppointmentResponse appointment,
      boolean alreadyConverted) {
    this.leadId = leadId;
    this.patientId = patientId;
    this.patient = patient;
    this.appointmentId = appointmentId;
    this.appointment = appointment;
    this.alreadyConverted = alreadyConverted;
  }

  public UUID getLeadId() {
    return leadId;
  }

  public void setLeadId(UUID leadId) {
    this.leadId = leadId;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public PatientResponse getPatient() {
    return patient;
  }

  public void setPatient(PatientResponse patient) {
    this.patient = patient;
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public AppointmentResponse getAppointment() {
    return appointment;
  }

  public void setAppointment(AppointmentResponse appointment) {
    this.appointment = appointment;
  }

  public boolean isAlreadyConverted() {
    return alreadyConverted;
  }

  public void setAlreadyConverted(boolean alreadyConverted) {
    this.alreadyConverted = alreadyConverted;
  }
}

