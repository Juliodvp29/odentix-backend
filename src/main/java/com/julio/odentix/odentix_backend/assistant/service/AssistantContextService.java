package com.julio.odentix.odentix_backend.assistant.service;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.billing.entity.InstallmentStatus;
import com.julio.odentix.odentix_backend.billing.repository.InstallmentRepository;
import com.julio.odentix.odentix_backend.crm.entity.LeadStatus;
import com.julio.odentix.odentix_backend.crm.repository.LeadRepository;
import com.julio.odentix.odentix_backend.inventory.entity.InventoryItem;
import com.julio.odentix.odentix_backend.inventory.repository.InventoryItemRepository;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Arma la foto de la clínica para el asistente (FASE10-01).
 *
 * <p>Todo sale del `TenantContext`: la foto contiene <b>solo</b> datos del
 * tenant activo, nunca de otra clínica (regla §5 de AGENTS.md). Reúsa
 * repositorios existentes sin queries nuevas salvo derivadas mínimas. Las
 * listas se acotan (top 5) para acotar tokens y costo por pregunta.
 */
@Service
public class AssistantContextService {

  /** Cuántas filas de detalle incluir por sección como máximo. */
  static final int TOP = 5;

  private final OpportunityRepository opportunityRepository;
  private final TreatmentPlanRepository treatmentPlanRepository;
  private final AppointmentRepository appointmentRepository;
  private final InstallmentRepository installmentRepository;
  private final InventoryItemRepository inventoryItemRepository;
  private final LeadRepository leadRepository;

  public AssistantContextService(
      OpportunityRepository opportunityRepository,
      TreatmentPlanRepository treatmentPlanRepository,
      AppointmentRepository appointmentRepository,
      InstallmentRepository installmentRepository,
      InventoryItemRepository inventoryItemRepository,
      LeadRepository leadRepository) {
    this.opportunityRepository = opportunityRepository;
    this.treatmentPlanRepository = treatmentPlanRepository;
    this.appointmentRepository = appointmentRepository;
    this.installmentRepository = installmentRepository;
    this.inventoryItemRepository = inventoryItemRepository;
    this.leadRepository = leadRepository;
  }

  /**
   * Foto en texto plano de la situación actual de la clínica.
   *
   * @param tenantId clínica activa.
   * @return resumen por secciones, listo para inyectar al prompt.
   */
  @Transactional(readOnly = true)
  public String snapshot(UUID tenantId) {
    TenantContext.getRequiredTenantId();
    StringBuilder foto = new StringBuilder();

    // Oportunidades abiertas por tipo + top por prioridad.
    List<Opportunity> abiertas = opportunityRepository
        .findAllByTenantIdOrderByPriorityDescDetectedAtDesc(tenantId).stream()
        .filter(o -> o.getStatus() == OpportunityStatus.abierta
            || o.getStatus() == OpportunityStatus.en_progreso)
        .toList();
    Map<String, Long> porTipo = abiertas.stream()
        .collect(Collectors.groupingBy(o -> o.getType().name(), Collectors.counting()));
    foto.append("Oportunidades abiertas: ").append(abiertas.size());
    if (!porTipo.isEmpty()) {
      foto.append(" (").append(porTipo.entrySet().stream()
          .map(e -> e.getKey() + ": " + e.getValue())
          .collect(Collectors.joining(", "))).append(")");
    }
    foto.append(".\n");
    abiertas.stream().limit(TOP).forEach(o -> foto.append("- [P").append(o.getPriority())
        .append("] ").append(o.getType()).append(" $").append(o.getEstimatedValueCop())
        .append(" (").append(o.getRelatedEntityType()).append(").\n"));

    // Planes sin decidir: conteo + valor total.
    List<TreatmentPlan> sinDecidir = new java.util.ArrayList<>();
    sinDecidir.addAll(treatmentPlanRepository.findByStatusAndTenantId(
        TreatmentPlanStatus.presentado, tenantId));
    sinDecidir.addAll(treatmentPlanRepository.findByStatusAndTenantId(
        TreatmentPlanStatus.en_decision, tenantId));
    BigDecimal totalPlanes = sinDecidir.stream()
        .map(p -> p.getTotalPriceCop() != null ? p.getTotalPriceCop() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    foto.append("Planes de tratamiento sin decidir: ").append(sinDecidir.size())
        .append(" por $").append(totalPlanes).append(" COP en total.\n");

    // Citas programadas próximas 24h sin confirmar.
    Instant ahora = Instant.now();
    List<Appointment> proximas = appointmentRepository
        .findByStatusAndStartsAtBetween(AppointmentStatus.programada, ahora,
            ahora.plus(Duration.ofHours(24)));
    foto.append("Citas programadas próximas 24h sin confirmar: ").append(proximas.size())
        .append(".\n");
    proximas.stream()
        .sorted(Comparator.comparing(Appointment::getStartsAt))
        .limit(TOP)
        .forEach(c -> foto.append("- ").append(c.getStartsAt())
            .append(" paciente: ").append(nombrePaciente(c)).append(".\n"));

    // Cartera vencida: conteo + total.
    List<Installment> vencidas =
        installmentRepository.findByStatus(InstallmentStatus.vencida);
    BigDecimal totalVencido = vencidas.stream()
        .map(c -> c.getAmountCop() != null ? c.getAmountCop() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    foto.append("Cuotas vencidas por cobrar: ").append(vencidas.size())
        .append(" por $").append(totalVencido).append(" COP en total.\n");

    // Inventario crítico.
    List<InventoryItem> criticos = inventoryItemRepository.findCritical(tenantId);
    foto.append("Insumos en nivel crítico: ").append(criticos.size()).append(".\n");
    criticos.stream().limit(TOP).forEach(i -> foto.append("- ").append(i.getName())
        .append(" (stock ").append(i.getQuantity())
        .append("/mínimo ").append(i.getMinThreshold()).append(").\n"));

    // Leads nuevos sin contactar.
    long leadsNuevos = leadRepository.findByTenantIdAndStatus(tenantId, LeadStatus.nuevo).size();
    foto.append("Leads nuevos sin contactar: ").append(leadsNuevos).append(".\n");

    return foto.toString();
  }

  private static String nombrePaciente(Appointment cita) {
    if (cita.getPatient() == null) {
      return "—";
    }
    return (cita.getPatient().getFirstName() + " " + cita.getPatient().getLastName()).strip();
  }
}
