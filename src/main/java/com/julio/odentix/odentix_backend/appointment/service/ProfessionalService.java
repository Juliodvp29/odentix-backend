package com.julio.odentix.odentix_backend.appointment.service;

import com.julio.odentix.odentix_backend.appointment.dto.CreateProfessionalRequest;
import com.julio.odentix.odentix_backend.appointment.dto.ProfessionalResponse;
import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para profesionales de la clínica.
 *
 * <p>El listado alimenta el selector de citas del front (incluye externos e
 * inactivos salvo filtro): por eso es legible por todos los roles de agenda,
 * mientras que la creación queda reservada al propietario porque
 * {@code isExternal} impacta nómina.
 *
 * <p>El tenant siempre sale del {@code TenantContext}: los profesionales de
 * otro tenant resultan invisibles (404/listas vacías), conforme a la regla
 * de aislamiento multi-tenant del proyecto.
 */
@Service
public class ProfessionalService {

  private final ProfessionalRepository professionalRepository;

  public ProfessionalService(ProfessionalRepository professionalRepository) {
    this.professionalRepository = professionalRepository;
  }

  /**
   * Lista los profesionales del tenant activo ordenados por nombre.
   *
   * <p>Los filtros se aplican en memoria sobre el listado ordenado: la
   * cardinalidad (planta de una clínica) no justifica una query por
   * combinación.
   *
   * @param onlyActive si es {@code true}, solo profesionales activos.
   * @param externalOnly si es {@code true}, solo especialistas externos.
   * @return profesionales del tenant activo en orden alfabético.
   */
  @Transactional(readOnly = true)
  public List<ProfessionalResponse> listarProfesionales(Boolean onlyActive, Boolean externalOnly) {
    TenantContext.getRequiredTenantId();

    // El repositorio filtra por el tenant activo de forma automática.
    return professionalRepository.findAllByOrderByFullNameAsc().stream()
        .filter(p -> !Boolean.TRUE.equals(onlyActive) || p.isActive())
        .filter(p -> !Boolean.TRUE.equals(externalOnly) || p.isExternal())
        .map(ProfessionalResponse::fromEntity)
        .toList();
  }

  /**
   * Crea un profesional en el tenant activo (activo y, por defecto, de planta).
   *
   * @param request nombre, especialidad, matrícula y marca de externo.
   * @return profesional creado.
   */
  @Transactional
  public ProfessionalResponse crearProfesional(CreateProfessionalRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Professional profesional = new Professional(tenantId, request.getFullName());
    profesional.setSpecialty(request.getSpecialty());
    profesional.setLicenseNumber(request.getLicenseNumber());
    profesional.setExternal(request.isExternal());

    return ProfessionalResponse.fromEntity(professionalRepository.save(profesional));
  }
}
