package com.julio.odentix.odentix_backend.billing.controller;

import com.julio.odentix.odentix_backend.billing.dto.CreateInvoiceRequest;
import com.julio.odentix.odentix_backend.billing.dto.CreatePaymentRequest;
import com.julio.odentix.odentix_backend.billing.dto.InvoiceResponse;
import com.julio.odentix.odentix_backend.billing.dto.PaymentResponse;
import com.julio.odentix.odentix_backend.billing.entity.InvoiceStatus;
import com.julio.odentix.odentix_backend.billing.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
   * Consulta una factura por ID dentro del tenant activo, con sus ítems.
   *
   * @param id identificador de la factura.
   * @return factura encontrada (404 si es de otro tenant).
   */
  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Consultar factura por ID",
      description = "Obtiene el detalle de una factura con sus ítems. Devuelve 404 si pertenece a otro tenant."
  )
  public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable UUID id) {
    return ResponseEntity.ok(invoiceService.getInvoiceById(id));
  }

  /**
   * Lista facturas del tenant activo con filtros opcionales y paginación.
   *
   * @param patientId filtra por paciente (opcional).
   * @param status filtra por estado (opcional).
   * @param pageable paginación y orden.
   * @return página de facturas con sus ítems.
   */
  @GetMapping
  @PreAuthorize("hasAnyRole('PROPIETARIO', 'ODONTOLOGO', 'RECEPCION', 'AUXILIAR')")
  @Operation(
      summary = "Listar facturas",
      description = "Consulta paginada de facturas del tenant con filtros opcionales por paciente y estado."
  )
  public ResponseEntity<Page<InvoiceResponse>> listInvoices(
      @RequestParam(required = false) UUID patientId,
      @RequestParam(required = false) InvoiceStatus status,
      @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable) {
    return ResponseEntity.ok(invoiceService.listInvoices(patientId, status, pageable));
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
