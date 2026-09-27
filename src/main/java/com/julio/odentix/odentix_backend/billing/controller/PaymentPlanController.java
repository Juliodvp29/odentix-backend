package com.julio.odentix.odentix_backend.billing.controller;

import com.julio.odentix.odentix_backend.billing.dto.CreatePaymentPlanRequest;
import com.julio.odentix.odentix_backend.billing.dto.PaymentPlanResponse;
import com.julio.odentix.odentix_backend.billing.service.PaymentPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para planes de pago en cuotas (FASE6-02).
 *
 * <p>Las rutas están anidadas bajo {@code /api/v1/treatment-plans/{id}} porque
 * un plan de pago siempre pertenece a un plan de tratamiento específico.
 */
@RestController
@RequestMapping("/api/v1/treatment-plans")
@Tag(name = "Cartera", description = "Planes de pago en cuotas y registro de pagos de cuotas.")
@SecurityRequirement(name = "bearerAuth")
public class PaymentPlanController {

  private final PaymentPlanService paymentPlanService;

  public PaymentPlanController(PaymentPlanService paymentPlanService) {
    this.paymentPlanService = paymentPlanService;
  }

  /**
   * Crea un plan de pago en N cuotas mensuales para un plan de tratamiento.
   *
   * <p>El monto se distribuye en cuotas iguales; el resto de redondeo se absorbe
   * en la última cuota. Si el tratamiento ya tiene un plan de pago activo,
   * devuelve {@code 409 Conflict}.
   *
   * @param id identificador del plan de tratamiento.
   * @param request monto total y número de cuotas.
   * @return plan de pago creado con sus cuotas en estado {@code pendiente}.
   */
  @PostMapping("/{id}/payment-plan")
  @PreAuthorize("@subscriptionService.requireFeature('cartera') and hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Crear plan de pago",
      description = "Crea un plan de pago en N cuotas mensuales para un plan de tratamiento. "
          + "Devuelve 409 si el tratamiento ya tiene un plan activo."
  )
  public ResponseEntity<PaymentPlanResponse> createPaymentPlan(
      @PathVariable UUID id,
      @Valid @RequestBody CreatePaymentPlanRequest request) {
    PaymentPlanResponse response = paymentPlanService.createPaymentPlan(id, request);
    URI location = ServletUriComponentsBuilder
        .fromCurrentContextPath()
        .path("/api/v1/treatment-plans/{treatmentId}/payment-plan")
        .buildAndExpand(id)
        .toUri();
    return ResponseEntity.created(location).body(response);
  }

  /**
   * Consulta el plan de pago de un plan de tratamiento con todas sus cuotas.
   *
   * <p>Devuelve {@code 404} si el tratamiento no existe, pertenece a otro
   * tenant o aún no tiene un plan de pago creado.
   *
   * @param id identificador del plan de tratamiento.
   * @return plan de pago con sus cuotas ordenadas por número.
   */
  @GetMapping("/{id}/payment-plan")
  @PreAuthorize("@subscriptionService.requireFeature('cartera') and hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Consultar plan de pago",
      description = "Obtiene el plan de pago de un plan de tratamiento con sus cuotas. "
          + "Devuelve 404 si el tratamiento es de otro tenant o no tiene plan."
  )
  public ResponseEntity<PaymentPlanResponse> getPaymentPlan(
      @PathVariable UUID id) {
    return ResponseEntity.ok(paymentPlanService.getPaymentPlanByTreatmentPlanId(id));
  }
}
