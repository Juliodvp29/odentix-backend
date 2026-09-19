package com.julio.odentix.odentix_backend.billing.controller;

import com.julio.odentix.odentix_backend.billing.dto.PortfolioSummaryResponse;
import com.julio.odentix.odentix_backend.billing.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para el dashboard de cartera de la clínica (FASE6-04).
 */
@RestController
@RequestMapping("/api/v1/portfolio")
@Tag(name = "Cartera", description = "Planes de pago en cuotas, registro de pagos de cuotas y dashboard de cartera.")
@SecurityRequirement(name = "bearerAuth")
public class PortfolioController {

  private final PortfolioService portfolioService;

  public PortfolioController(PortfolioService portfolioService) {
    this.portfolioService = portfolioService;
  }

  /**
   * Obtiene el resumen financiero consolidado de cartera de la clínica activa.
   *
   * @return indicadores de cartera total, vencida, por vencer y al día.
   */
  @GetMapping("/summary")
  @PreAuthorize("@subscriptionService.requireFeature('cartera') and hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Resumen de cartera",
      description = "Devuelve los totales financieros consolidados de cartera para la clínica: "
          + "cartera total pactada, vencida, por vencer, al día (pagada) y saldo pendiente."
  )
  public ResponseEntity<PortfolioSummaryResponse> getSummary() {
    return ResponseEntity.ok(portfolioService.getSummary());
  }
}
