package com.julio.odentix.odentix_backend.appointment.service;

import com.julio.odentix.odentix_backend.appointment.dto.RoomResponse;
import com.julio.odentix.odentix_backend.appointment.repository.RoomRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para consultorios de la clínica.
 *
 * <p>El listado alimenta el selector de agenda del front: es legible por
 * todos los roles de agenda. Incluye inactivos (el front filtra si quiere),
 * igual que el listado de profesionales.
 *
 * <p>El tenant siempre sale del {@code TenantContext}, conforme a la regla
 * de aislamiento multi-tenant del proyecto.
 */
@Service
public class RoomService {

  private final RoomRepository roomRepository;

  public RoomService(RoomRepository roomRepository) {
    this.roomRepository = roomRepository;
  }

  /**
   * Lista los consultorios del tenant activo ordenados por nombre.
   *
   * @return consultorios del tenant activo en orden alfabético.
   */
  @Transactional(readOnly = true)
  public List<RoomResponse> listarConsultorios() {
    TenantContext.getRequiredTenantId();

    // El repositorio filtra por el tenant activo de forma automática.
    return roomRepository.findAllByOrderByNameAsc().stream()
        .map(RoomResponse::fromEntity)
        .toList();
  }
}
