package com.julio.odentix.odentix_backend.assistant.dto;

/**
 * Respuesta del asistente con el modelo usado (FASE10-01). Sin Lombok (§9).
 */
public class AskResponse {

  private String answer;
  private String model;

  public AskResponse() {
  }

  public String getAnswer() {
    return answer;
  }

  public void setAnswer(String answer) {
    this.answer = answer;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }
}
