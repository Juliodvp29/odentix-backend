package com.julio.odentix.odentix_backend.assistant.dto;

/**
 * Mensaje sugerido sin enviar (FASE10-02).
 *
 * <p>`suggestedChannel` orienta a la UI por dónde enviarlo cuando el humano lo
 * apruebe; el endpoint jamás envía ni persiste nada. Sin Lombok (§9).
 */
public class SuggestMessageResponse {

  private String message;
  private String suggestedChannel;
  private String model;

  public SuggestMessageResponse() {
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getSuggestedChannel() {
    return suggestedChannel;
  }

  public void setSuggestedChannel(String suggestedChannel) {
    this.suggestedChannel = suggestedChannel;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }
}
