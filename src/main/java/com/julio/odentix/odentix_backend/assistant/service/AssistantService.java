package com.julio.odentix.odentix_backend.assistant.service;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.assistant.client.GroqChatClient;
import com.julio.odentix.odentix_backend.assistant.AssistantException;
import com.julio.odentix.odentix_backend.assistant.dto.AskRequest;
import com.julio.odentix.odentix_backend.assistant.dto.AskResponse;
import com.julio.odentix.odentix_backend.assistant.dto.SuggestMessageRequest;
import com.julio.odentix.odentix_backend.assistant.dto.SuggestMessageResponse;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asistente administrativo (FASE10-01: preguntas simples).
 *
 * <p>Resuelve la pregunta consultando datos ya expuestos por el dominio: arma
 * la foto del tenant activo y le pide al proveedor que responda <b>solo</b>
 * con esos datos. El system prompt prohíbe inventar cifras y exige decir qué
 * falta si los datos no alcanzan.
 */
@Service
public class AssistantService {

  private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

  static final String SYSTEM_PROMPT = """
      Eres el asistente administrativo de una clínica odontológica. Responde en español,
      breve y accionable. Usa ÚNICAMENTE los datos de la clínica que siguen a continuación.
      Está prohibido inventar cifras, nombres o fechas que no estén en esos datos.
      Si los datos no alcanzan para responder, dilo y pide qué información falta.
      """;

  static final String SUGGEST_PROMPT = """
      Redactas mensajes cortos para una clínica odontológica. Responde SOLO con el
      mensaje listo para enviar, en español, tono cordial, sin comillas ni
      explicaciones. Usa únicamente los datos dados; está prohibido inventar
      nombres, fechas u ofertas. Máximo 300 caracteres si el canal es whatsapp.
      """;

  private final AssistantContextService contextService;
  private final GroqChatClient groqChatClient;
  private final AppointmentRepository appointmentRepository;

  public AssistantService(
      AssistantContextService contextService,
      GroqChatClient groqChatClient,
      AppointmentRepository appointmentRepository) {
    this.contextService = contextService;
    this.groqChatClient = groqChatClient;
    this.appointmentRepository = appointmentRepository;
  }

  /**
   * Responde una pregunta con los datos reales del tenant activo.
   *
   * @param request pregunta en lenguaje natural.
   * @return respuesta del proveedor con el modelo usado.
   */
  @Transactional(readOnly = true)
  public AskResponse ask(AskRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    String foto = contextService.snapshot(tenantId);

    List<Map<String, String>> messages = List.of(
        Map.of("role", "system", "content", SYSTEM_PROMPT),
        Map.of("role", "user",
            "content", "Datos de mi clínica:\n" + foto + "\nPregunta: " + request.getQuestion()));

    AskResponse response = new AskResponse();
    response.setModel(groqChatClient.getModel());
    Instant inicio = Instant.now();
    try {
      response.setAnswer(groqChatClient.chat(messages, 0.2, 500));
      return response;
    } catch (AssistantException e) {
      // FASE10-03: sin proveedor no se rompe nada — plantilla fija con datos
      // reales + flag, y el fallo queda registrado (ver registrarFallo).
      registrarFallo("ask", inicio, e);
      response.setAnswer("Proveedor de IA no disponible. Resumen actual de tu clínica:\n" + foto);
      response.setFallback(true);
      return response;
    }
  }

  /**
   * Sugiere un mensaje para una cita sin enviarlo ni persistirlo (FASE10-02).
   *
   * <p>Es solo una sugerencia editable: el envío pasa después por el flujo de
   * notificaciones con confirmación humana. Verificable por ausencia de filas
   * en `notifications` y `tasks` tras la llamada.
   *
   * @param request cita de contexto y matiz opcional.
   * @return mensaje sugerido, canal sugerido y modelo usado.
   */
  @Transactional(readOnly = true)
  public SuggestMessageResponse suggestMessage(SuggestMessageRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();
    Appointment cita = request.getAppointmentId() != null
        ? appointmentRepository.findByIdAndTenantId(request.getAppointmentId(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Cita no encontrada: " + request.getAppointmentId()))
        : null;

    String nombre = "el paciente";
    String telefono = null;
    String email = null;
    String detalleCita = "sin cita asociada";
    if (cita != null) {
      if (cita.getPatient() != null) {
        nombre = (cita.getPatient().getFirstName() + " " + cita.getPatient().getLastName())
            .strip();
        telefono = cita.getPatient().getPhone();
        email = cita.getPatient().getEmail();
      }
      detalleCita = "cita del " + cita.getStartsAt() + " (estado " + cita.getStatus() + ")";
    }
    String canal = telefono != null && !telefono.isBlank() ? "whatsapp"
        : email != null && !email.isBlank() ? "email" : "whatsapp";

    StringBuilder datos = new StringBuilder();
    datos.append("Paciente: ").append(nombre).append(".\n");
    datos.append("Contexto: ").append(detalleCita).append(".\n");
    datos.append("Canal previsto: ").append(canal).append(".\n");
    if (request.getHint() != null && !request.getHint().isBlank()) {
      datos.append("Matiz pedido: ").append(request.getHint().strip()).append(".\n");
    }

    List<Map<String, String>> messages = List.of(
        Map.of("role", "system", "content", SUGGEST_PROMPT),
        Map.of("role", "user", "content", datos.toString()));

    SuggestMessageResponse response = new SuggestMessageResponse();
    response.setSuggestedChannel(canal);
    response.setModel(groqChatClient.getModel());
    Instant inicio = Instant.now();
    try {
      response.setMessage(groqChatClient.chat(messages, 0.5, 300));
      return response;
    } catch (AssistantException e) {
      // FASE10-03: plantilla fija con los datos del contexto, sin propagar.
      registrarFallo("suggest-message", inicio, e);
      response.setMessage("Hola " + nombre + ", te recordamos tu " + detalleCita
          + ". Confirma tu asistencia respondiendo este mensaje.");
      response.setFallback(true);
      return response;
    }
  }

  /**
   * Registra el fallo del proveedor (FASE10-03): log estructurado sin PII del
   * paciente (modelo, latencia y error) para operar el endpoint en producción.
   * No se persiste por paciente porque no hay un intento que auditar —el
   * fallback ya responde con datos reales.
   */
  private void registrarFallo(String operacion, Instant inicio, AssistantException e) {
    long latenciaMs = Instant.now().toEpochMilli() - inicio.toEpochMilli();
    String detalle = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    if (detalle.length() > 200) {
      detalle = detalle.substring(0, 200);
    }
    log.warn("Asistente IA sin proveedor (operacion={}, modelo={}, latenciaMs={}): {}",
        operacion, groqChatClient.getModel(), latenciaMs, detalle);
  }
}
