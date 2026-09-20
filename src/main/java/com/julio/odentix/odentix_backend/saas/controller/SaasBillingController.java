package com.julio.odentix.odentix_backend.saas.controller;

import com.julio.odentix.odentix_backend.auth.exception.RateLimitExceededException;
import com.julio.odentix.odentix_backend.auth.service.PublicEndpointRateLimitService;
import com.julio.odentix.odentix_backend.saas.dto.CheckoutRequest;
import com.julio.odentix.odentix_backend.saas.dto.CheckoutResponse;
import com.julio.odentix.odentix_backend.saas.dto.SubscriptionResponse;
import com.julio.odentix.odentix_backend.saas.service.SaasBillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
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
  private final PublicEndpointRateLimitService publicEndpointRateLimitService;

  public SaasBillingController(
      SaasBillingService billingService,
      PublicEndpointRateLimitService publicEndpointRateLimitService) {
    this.billingService = billingService;
    this.publicEndpointRateLimitService = publicEndpointRateLimitService;
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
   * Suscripción actual: plan, ciclo, estado y periodo (para mostrar y cambiar).
   */
  @GetMapping("/subscription")
  @PreAuthorize("hasRole('PROPIETARIO')")
  @Operation(
      summary = "Ver suscripción",
      description = "Suscripción viva actual con precios mensual/anual para elegir ciclo."
  )
  public ResponseEntity<SubscriptionResponse> miSuscripcion() {
    return ResponseEntity.ok(billingService.miSuscripcion());
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
      @RequestHeader(value = "x-bold-signature", required = false) String signature,
      HttpServletRequest httpRequest) {
    // FASE12-01: el webhook es público (sin JWT) y recibe reintentos de Bold;
    // el límite es generoso para no romper reintentos legítimos, pero frena spam/DoS.
    publicEndpointRateLimitService.checkWebhookLimit(extractClientIp(httpRequest));
    if (!billingService.firmaValida(rawBody, signature)) {
      return ResponseEntity.badRequest().build();
    }
    String[] evento = billingService.parsearEvento(rawBody);
    billingService.aplicarEvento(evento[0], evento[1], evento[2]);
    return ResponseEntity.ok().build();
  }

  @ExceptionHandler(RateLimitExceededException.class)
  public ResponseEntity<Map<String, Object>> handleRateLimitExceeded(RateLimitExceededException ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
        .header("Retry-After", String.valueOf(ex.getRetryAfterSeconds()))
        .body(Map.of(
            "error", "Límite de peticiones excedido",
            "message", ex.getMessage(),
            "retryAfterSeconds", ex.getRetryAfterSeconds()
        ));
  }

  private String extractClientIp(HttpServletRequest request) {
    if (request == null) {
      return "unknown";
    }
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    String remoteAddr = request.getRemoteAddr();
    return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "unknown";
  }
}
