package com.julio.odentix.odentix_backend.appointment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.julio.odentix.odentix_backend.appointment.entity.Room;
import java.util.UUID;

/**
 * Consultorio de la clínica para el selector de agenda.
 *
 * <p>El booleano se expone como {@code isActive} por contrato con el front
 * (mismo criterio que {@code ProfessionalResponse}); sin la anotación
 * Jackson serializaría {@code active}.
 *
 * <p>La entidad JPA nunca sale por la API.
 *
 * <p>Convención: Sin Lombok (código explícito).
 */
public class RoomResponse {

  private UUID id;
  private String name;
  private boolean active;

  public RoomResponse() {
  }

  /**
   * Construye el DTO desde la entidad.
   */
  public static RoomResponse fromEntity(Room room) {
    RoomResponse r = new RoomResponse();
    r.id = room.getId();
    r.name = room.getName();
    r.active = room.isActive();
    return r;
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @JsonProperty("isActive")
  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
