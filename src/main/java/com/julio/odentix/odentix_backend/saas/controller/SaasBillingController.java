package com.julio.odentix.odentix_backend.saas.controller;

import com.julio.odentix.odentix_backend.saas.dto.CheckoutRequest;
import com.julio.odentix.odentix_backend.saas.dto.CheckoutResponse;
import com.julio.odentix.odentix_backend.saas.service.SaasBillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST de facturación del propio SaaS con Bold (FASE11-04).
 */
@RestController
@RequestMapping("/api/v1/billing")
@Tag(name = "Facturación SaaS", description = "Checkout de planes y webhook de Bold.")
@SecurityRequirement(name = "bearerAuth")
public class SaasBillingController {

  private final SaasBillingService billingService;

  public SaasBillingController(SaasBillingService billingService) {
    this.billingService = billingService;
  }

  /**
   * Inicia el checkout: fija plan+ciclo y devuelve la URL de pago de Bold.
   * Idempotente por ciclo (reutiliza el link pendiente si existe).
   */
  @PostMapping("/checkout")
  @PreAuthorize("hasRole('PROPIETARIO')")
  @Operation(
      summary = "Iniciar checkout",
      description = "Crea o actualiza la suscripción y genera el link de pago de Bold."
  )
  public ResponseEntity<CheckoutResponse> checkout(
      @Valid @RequestBody CheckoutRequest request) {
    return ResponseEntity.ok(billingService.checkout(request));
  }

  /**
   * Webhook de Bold (CloudEvents). Público: Bold lo llama sin JWT y se
   * autentica por firma HMAC (`x-bold-signature`); firma mala → 400.
   * Responde 200 inmediato como exige Bold (&lt;2s); los reintentos se
   * absorben por idempotencia.
   */
  @PostMapping(
      path = "/webhooks/bold",
      consumes = MediaType.APPLICATION_JSON_VALUE)
  @Operation(
      summary = "Webhook de Bold",
      description = "Recibe eventos de pago (SALE_APPROVED/_REJECTED, VOID_*) con "
          + "firma HMAC y actualiza suscripción y cobro."
  )
  public ResponseEntity<Void> webhookBold(
      @RequestBody String rawBody,
      @RequestHeader(value = "x-bold-signature", required = false) String signature) {
    if (!billingService.firmaValida(rawBody, signature)) {
      return ResponseEntity.badRequest().build();
    }
    String[] evento = billingService.parsearEvento(rawBody);
    billingService.aplicarEvento(evento[0], evento[1], evento[2]);
    return ResponseEntity.ok().build();
  }
}
