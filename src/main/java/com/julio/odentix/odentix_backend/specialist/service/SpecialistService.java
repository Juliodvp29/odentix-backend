package com.julio.odentix.odentix_backend.specialist.service;

import com.julio.odentix.odentix_backend.appointment.entity.Professional;
import com.julio.odentix.odentix_backend.appointment.repository.ProfessionalRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ConflictException;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.specialist.dto.CreateSpecialistRequest;
import com.julio.odentix.odentix_backend.specialist.dto.SpecialistResponse;
import com.julio.odentix.odentix_backend.specialist.entity.Specialist;
import com.julio.odentix.odentix_backend.specialist.repository.SpecialistRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Servicio de negocio para fichas de especialistas externos.
 *
 * <p>La ficha pacta el porcentaje de honorarios y las condiciones de pago de
 * un profesional externo: operación financiera sensible, reservada al
 * propietario. Reutiliza la validación de profesional externo en capa de
 * aplicación (el trigger de BD y el callback de la entidad son la segunda y
 * tercera capa); un profesional no externo se rechaza con 400 antes de
 * llegar a BD.
 *
 * <p>El tenant siempre sale del {@code TenantContext}: un profesional de otro
 * tenant resulta invisible (404), conforme a la regla de aislamiento
 * multi-tenant del proyecto.
 */
@Service
public class SpecialistService {

  private final SpecialistRepository specialistRepository;
  private final ProfessionalRepository professionalRepository;

  public SpecialistService(
      SpecialistRepository specialistRepository,
      ProfessionalRepository professionalRepository) {
    this.specialistRepository = specialistRepository;
    this.professionalRepository = professionalRepository;
  }

  /**
   * Crea la ficha financiera de un especialista externo.
   *
   * <p>Política de una ficha por profesional: si ya existe, se lanza
   * {@link ConflictException} con HTTP 409. La constraint UNIQUE de BD es la
   * capa autoritativa ante condiciones de carrera.
   *
   * @param request profesional, porcentaje de honorarios y términos de pago.
   * @return ficha creada.
   */
  @Transactional
  public SpecialistResponse crearEspecialista(CreateSpecialistRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Professional profesional = professionalRepository
        .findByIdAndTenantId(request.getProfessionalId(), tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Profesional no encontrado: " + request.getProfessionalId()));

    if (!profesional.isExternal()) {
      throw new IllegalArgumentException(
          "El profesional debe ser externo (isExternal = true) para tener ficha de especialista.");
    }

    if (specialistRepository.findByProfessionalId(profesional.getId()).isPresent()) {
      throw new ConflictException(
          "El profesional ya tiene una ficha de especialista.");
    }

    Specialist ficha = new Specialist(tenantId, profesional, request.getFeePercentage());
    ficha.setPaymentTerms(request.getPaymentTerms());

    return SpecialistResponse.fromEntity(specialistRepository.save(ficha));
  }
}
