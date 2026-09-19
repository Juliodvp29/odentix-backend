package com.julio.odentix.odentix_backend.opportunity.service;

import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.billing.repository.InstallmentRepository;
import com.julio.odentix.odentix_backend.billing.repository.PaymentPlanRepository;
import com.julio.odentix.odentix_backend.crm.repository.LeadRepository;
import com.julio.odentix.odentix_backend.notification.entity.Notification;
import com.julio.odentix.odentix_backend.notification.entity.NotificationChannel;
import com.julio.odentix.odentix_backend.notification.service.NotificationService;
import com.julio.odentix.odentix_backend.opportunity.dto.OpportunityActionResponse;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityAction;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityActionRepository;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ConflictException;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.task.entity.Task;
import com.julio.odentix.odentix_backend.task.entity.TaskPriority;
import com.julio.odentix.odentix_backend.task.repository.TaskRepository;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ejecución de acciones sugeridas de oportunidad (FASE9-03: "ejecutar").
 *
 * <p>`crear_tarea` crea una `Task` vinculada a la oportunidad;
 * `enviar_mensaje` resuelve el destinatario fresco (el mensaje se congeló al
 * detectar, pero el contacto pudo cambiar — si ya no hay, 409 en vez de
 * enviar a un dato viejo) y envía por el canal guardado vía
 * `NotificationService` (que nunca lanza: si queda `fallida`, la acción igual
 * se marca ejecutada porque el intento fue real).
 *
 * <p>Una acción ejecutada no se puede re-ejecutar (409). El tenant siempre sale
 * del `TenantContext` (404 si es de otra clínica).
 */
@Service
public class OpportunityActionService {

  private final OpportunityRepository opportunityRepository;
  private final OpportunityActionRepository actionRepository;
  private final TaskRepository taskRepository;
  private final NotificationService notificationService;
  private final LeadRepository leadRepository;
  private final AppointmentRepository appointmentRepository;
  private final TreatmentPlanRepository treatmentPlanRepository;
  private final PatientRepository patientRepository;
  private final InstallmentRepository installmentRepository;
  private final PaymentPlanRepository paymentPlanRepository;

  public OpportunityActionService(
      OpportunityRepository opportunityRepository,
      OpportunityActionRepository actionRepository,
      TaskRepository taskRepository,
      NotificationService notificationService,
      LeadRepository leadRepository,
      AppointmentRepository appointmentRepository,
      TreatmentPlanRepository treatmentPlanRepository,
      PatientRepository patientRepository,
      InstallmentRepository installmentRepository,
      PaymentPlanRepository paymentPlanRepository) {
    this.opportunityRepository = opportunityRepository;
    this.actionRepository = actionRepository;
    this.taskRepository = taskRepository;
    this.notificationService = notificationService;
    this.leadRepository = leadRepository;
    this.appointmentRepository = appointmentRepository;
    this.treatmentPlanRepository = treatmentPlanRepository;
    this.patientRepository = patientRepository;
    this.installmentRepository = installmentRepository;
    this.paymentPlanRepository = paymentPlanRepository;
  }

  /**
   * Ejecuta una acción sugerida.
   *
   * @param opportunityId oportunidad dentro del tenant activo.
   * @param actionId acción perteneciente a esa oportunidad.
   * @param currentUserId usuario que ejecuta (queda en `executed_by`).
   * @return acción ejecutada, con el `taskId` o `notificationId` resultante.
   */
  @Transactional
  public OpportunityActionResponse execute(UUID opportunityId, UUID actionId, UUID currentUserId) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Opportunity oportunidad = opportunityRepository.findById(opportunityId)
        .filter(o -> tenantId.equals(o.getTenantId()))
        .orElseThrow(() -> new ResourceNotFoundException(
            "Oportunidad no encontrada: " + opportunityId));
    OpportunityAction accion = actionRepository.findByIdAndTenantId(actionId, tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Acción no encontrada: " + actionId));
    if (!oportunidad.getId().equals(
        accion.getOpportunity() != null ? accion.getOpportunity().getId() : null)) {
      throw new ResourceNotFoundException("Acción no encontrada: " + actionId);
    }
    if (accion.isExecuted()) {
      throw new ConflictException("La acción ya fue ejecutada.");
    }

    OpportunityActionResponse response = OpportunityActionResponse.fromEntity(accion);
    switch (accion.getActionType()) {
      case crear_tarea -> response.setTaskId(
          crearTarea(oportunidad, accion).getId());
      case enviar_mensaje -> response.setNotificationId(
          enviarMensaje(oportunidad, accion).getId());
    }

    accion.setExecuted(true);
    accion.setExecutedAt(Instant.now());
    accion.setExecutedBy(currentUserId);
    actionRepository.save(accion);

    response.setExecuted(true);
    response.setExecutedAt(accion.getExecutedAt());
    return response;
  }

  private Task crearTarea(Opportunity oportunidad, OpportunityAction accion) {
    // El título va en la primera línea del mensaje sugerido (el esquema no
    // trae columna de título); el resto es la descripción.
    String sugerido = accion.getSuggestedMessage() != null ? accion.getSuggestedMessage() : "";
    String[] partes = sugerido.split("\n", 2);
    Task tarea = new Task(oportunidad.getTenantId(), partes[0].isBlank()
        ? "Atender oportunidad " + oportunidad.getType()
        : partes[0].strip());
    tarea.setDescription(partes.length > 1 ? partes[1].strip() : sugerido);
    tarea.setRelatedEntityType("opportunity");
    tarea.setRelatedEntityId(oportunidad.getId());
    tarea.setPriority(prioridadTarea(oportunidad.getPriority()));
    return taskRepository.save(tarea);
  }

  private static TaskPriority prioridadTarea(short prioridadOportunidad) {
    if (prioridadOportunidad >= 4) {
      return TaskPriority.alta;
    }
    if (prioridadOportunidad == 3) {
      return TaskPriority.media;
    }
    return TaskPriority.baja;
  }

  private Notification enviarMensaje(Opportunity oportunidad, OpportunityAction accion) {
    NotificationChannel canal;
    try {
      canal = NotificationChannel.valueOf(accion.getChannel());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new ConflictException(
          "La acción no tiene un canal de envío válido: " + accion.getChannel());
    }

    Patient paciente = resolverPaciente(oportunidad);
    String destinatario = resolverDestinatario(oportunidad, paciente, canal);
    if (destinatario == null || destinatario.isBlank()) {
      throw new ConflictException(
          "La oportunidad ya no tiene destinatario para " + canal + ".");
    }

    String asunto = canal == NotificationChannel.email ? "Mensaje de tu clínica" : "";
    return notificationService.sendCustomMessage(
        oportunidad.getTenantId(),
        canal,
        destinatario.strip(),
        asunto,
        accion.getSuggestedMessage() != null ? accion.getSuggestedMessage() : "",
        "oportunidad_accion",
        paciente != null ? paciente.getId() : null,
        Map.of(
            "opportunityId", oportunidad.getId().toString(),
            "opportunityType", oportunidad.getType().name()));
  }

  /**
   * Resuelve el destinatario fresco según el canal. Para leads se usa el
   * contacto del propio lead (puede no ser paciente todavía); para el resto,
   * el contacto del paciente de la entidad origen.
   */
  private String resolverDestinatario(
      Opportunity oportunidad, Patient paciente, NotificationChannel canal) {
    if ("lead".equals(oportunidad.getRelatedEntityType())
        && oportunidad.getRelatedEntityId() != null) {
      return leadRepository.findById(oportunidad.getRelatedEntityId())
          .map(lead -> canal == NotificationChannel.whatsapp ? lead.getPhone() : lead.getEmail())
          .orElse(null);
    }
    if (paciente == null) {
      return null;
    }
    return canal == NotificationChannel.whatsapp ? paciente.getPhone() : paciente.getEmail();
  }

  /**
   * Resuelve el paciente de la entidad origen para el registro de auditoría.
   * Sin paciente (inventario, espacio, lead aún no convertido) → null.
   */
  private Patient resolverPaciente(Opportunity oportunidad) {
    String tipo = oportunidad.getRelatedEntityType();
    UUID id = oportunidad.getRelatedEntityId();
    if (tipo == null || id == null) {
      return null;
    }
    return switch (tipo) {
      case "appointment" -> appointmentRepository.findById(id)
          .map(a -> a.getPatient()).orElse(null);
      case "treatment_plan" -> treatmentPlanRepository.findById(id)
          .map(t -> t.getPatient()).orElse(null);
      case "patient" -> patientRepository.findById(id).orElse(null);
      case "installment" -> installmentRepository.findById(id)
          .map(cuota -> paymentPlanRepository.findById(cuota.getPaymentPlan().getId())
              .map(plan -> treatmentPlanRepository
                  .findById(plan.getTreatmentPlanId()).map(
                      com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan::getPatient)
                  .orElse(null))
              .orElse(null))
          .orElse(null);
      default -> null;
    };
  }
}
