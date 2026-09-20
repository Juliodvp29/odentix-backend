package com.julio.odentix.odentix_backend.crm.dto;

import jakarta.validation.Valid;

/**
 * Solicitud para convertir un prospecto comercial (Lead) en un paciente activo,
 * con la opción de programar su primera cita en la misma operación (FASE5-03).
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class ConvertLeadRequest {

  @Valid
  private ConvertLeadPatientData patient;

  @Valid
  private ConvertLeadAppointmentData appointment;

  public ConvertLeadRequest() {
  }

  public ConvertLeadRequest(ConvertLeadPatientData patient, ConvertLeadAppointmentData appointment) {
    this.patient = patient;
    this.appointment = appointment;
  }

  public ConvertLeadPatientData getPatient() {
    return patient;
  }

  public void setPatient(ConvertLeadPatientData patient) {
    this.patient = patient;
  }

  public ConvertLeadAppointmentData getAppointment() {
    return appointment;
  }

  public void setAppointment(ConvertLeadAppointmentData appointment) {
    this.appointment = appointment;
  }
}

