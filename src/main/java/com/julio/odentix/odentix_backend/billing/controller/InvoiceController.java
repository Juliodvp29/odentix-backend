package com.julio.odentix.odentix_backend.billing.controller;

import com.julio.odentix.odentix_backend.billing.dto.CreateInvoiceRequest;
import com.julio.odentix.odentix_backend.billing.dto.CreatePaymentRequest;
import com.julio.odentix.odentix_backend.billing.dto.InvoiceResponse;
import com.julio.odentix.odentix_backend.billing.dto.PaymentResponse;
import com.julio.odentix.odentix_backend.billing.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Controlador REST para facturación simple y pagos (FASE4-04).
 */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Facturación", description = "Generación de facturas simples y registro de pagos.")
@SecurityRequirement(name = "bearerAuth")
public class InvoiceController {

  private final InvoiceService invoiceService;

  public InvoiceController(InvoiceService invoiceService) {
    this.invoiceService = invoiceService;
  }

  /**
   * Genera una factura manual o desde un plan de tratamiento.
   *
   * @param request paciente/plan, descuento e ítems manuales.
   * @return factura creada con código HTTP 201 Created.
   */
  @PostMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Generar factura",
      description = "Crea una factura con numeración secuencial, opcionalmente desde un plan de tratamiento (ítems y paciente inferidos)."
  )
  public ResponseEntity<InvoiceResponse> createInvoice(
      @Valid @RequestBody CreateInvoiceRequest request) {
    InvoiceResponse response = invoiceService.createInvoice(request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{id}")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }

  /**
   * Registra un pago parcial o total contra una factura.
   *
   * @param id identificador de la factura dentro del tenant activo.
   * @param request monto, medio y referencia opcional.
   * @return pago creado con el estado recalculado de la factura.
   */
  @PostMapping("/{id}/payments")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Registrar pago",
      description = "Registra un pago y actualiza el estado de la factura (pendiente → parcial → pagada). El sobrepago devuelve HTTP 400."
  )
  public ResponseEntity<PaymentResponse> registerPayment(
      @PathVariable UUID id,
      @Valid @RequestBody CreatePaymentRequest request) {
    PaymentResponse response = invoiceService.registerPayment(id, request);
    URI location = ServletUriComponentsBuilder.fromCurrentRequest()
        .path("/{paymentId}")
        .buildAndExpand(response.getId())
        .toUri();
    return ResponseEntity.created(location).body(response);
  }
}
