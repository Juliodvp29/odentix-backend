package com.julio.odentix.odentix_backend.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Pregunta en lenguaje natural al asistente (FASE10-01). Sin Lombok (§9).
 */
public class AskRequest {

  @NotBlank(message = "question es obligatoria")
  @Size(max = 500, message = "question no puede superar 500 caracteres")
  private String question;

  public AskRequest() {
  }

  public AskRequest(String question) {
    this.question = question;
  }

  public String getQuestion() {
    return question;
  }

  public void setQuestion(String question) {
    this.question = question;
  }
}
