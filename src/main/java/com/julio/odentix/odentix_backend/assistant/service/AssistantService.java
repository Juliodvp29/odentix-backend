package com.julio.odentix.odentix_backend.assistant.service;

import com.julio.odentix.odentix_backend.assistant.client.GroqChatClient;
import com.julio.odentix.odentix_backend.assistant.dto.AskRequest;
import com.julio.odentix.odentix_backend.assistant.dto.AskResponse;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

  static final String SYSTEM_PROMPT = """
      Eres el asistente administrativo de una clínica odontológica. Responde en español,
      breve y accionable. Usa ÚNICAMENTE los datos de la clínica que siguen a continuación.
      Está prohibido inventar cifras, nombres o fechas que no estén en esos datos.
      Si los datos no alcanzan para responder, dilo y pide qué información falta.
      """;

  private final AssistantContextService contextService;
  private final GroqChatClient groqChatClient;

  public AssistantService(
      AssistantContextService contextService, GroqChatClient groqChatClient) {
    this.contextService = contextService;
    this.groqChatClient = groqChatClient;
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

    String answer = groqChatClient.chat(messages, 0.2, 500);

    AskResponse response = new AskResponse();
    response.setAnswer(answer);
    response.setModel(groqChatClient.getModel());
    return response;
  }
}
