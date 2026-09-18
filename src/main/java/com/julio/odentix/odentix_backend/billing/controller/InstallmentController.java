package com.julio.odentix.odentix_backend.billing.controller;

import com.julio.odentix.odentix_backend.billing.dto.InstallmentResponse;
import com.julio.odentix.odentix_backend.billing.service.PaymentPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para operaciones sobre cuotas individuales (FASE6-02).
 */
@RestController
@RequestMapping("/api/v1/installments")
@Tag(name = "Cartera", description = "Planes de pago en cuotas y registro de pagos de cuotas.")
@SecurityRequirement(name = "bearerAuth")
public class InstallmentController {

  private final PaymentPlanService paymentPlanService;

  public InstallmentController(PaymentPlanService paymentPlanService) {
    this.paymentPlanService = paymentPlanService;
  }

  /**
   * Marca una cuota como pagada y genera la factura + pago automáticos.
   *
   * <p>La factura generada queda en estado {@code pagada}; el operador puede
   * consultarla desde el módulo de facturación para su trazabilidad.
   * Devuelve {@code 409 Conflict} si la cuota ya fue pagada previamente.
   *
   * @param id identificador de la cuota dentro del tenant activo.
   * @return cuota actualizada con estado {@code pagada} y {@code paidAt} registrado.
   */
  @PostMapping("/{id}/pay")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Pagar cuota",
      description = "Marca la cuota como pagada y genera la factura e Invoice correspondientes. "
          + "Devuelve 409 si la cuota ya estaba pagada."
  )
  public ResponseEntity<InstallmentResponse> payInstallment(@PathVariable UUID id) {
    InstallmentResponse response = paymentPlanService.payInstallment(id);
    return ResponseEntity.ok(response);
  }
}
