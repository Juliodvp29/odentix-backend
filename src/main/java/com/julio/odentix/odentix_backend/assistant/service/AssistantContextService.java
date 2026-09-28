package com.julio.odentix.odentix_backend.assistant.service;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.appointment.entity.AppointmentStatus;
import com.julio.odentix.odentix_backend.appointment.repository.AppointmentRepository;
import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.billing.entity.InstallmentStatus;
import com.julio.odentix.odentix_backend.billing.repository.InstallmentRepository;
import com.julio.odentix.odentix_backend.billing.repository.PaymentPlanRepository;
import com.julio.odentix.odentix_backend.crm.entity.Lead;
import com.julio.odentix.odentix_backend.crm.entity.LeadStatus;
import com.julio.odentix.odentix_backend.crm.repository.LeadRepository;
import com.julio.odentix.odentix_backend.inventory.entity.InventoryItem;
import com.julio.odentix.odentix_backend.inventory.repository.InventoryItemRepository;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.PatientRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlanStatus;
import com.julio.odentix.odentix_backend.treatmentplan.repository.TreatmentPlanRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
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
 * tenant activo, nunca de otra clínica (regla de aislamiento multi-tenant del proyecto). Reúsa
 * repositorios existentes sin queries nuevas salvo derivadas mínimas. Las
 * listas se acotan (top 5) para acotar tokens y costo por pregunta.
 */
@Service
public class AssistantContextService {

  /** Cuántas filas de detalle incluir por sección como máximo. */
  static final int TOP = 5;

  /**
   * Zona horaria para presentar fechas en el snapshot (días calendario de
   * la clínica, no UTC crudo).
   */
  private static final ZoneId ZONA_CLINICA = ZoneId.of("America/Bogota");

  private final OpportunityRepository opportunityRepository;
  private final TreatmentPlanRepository treatmentPlanRepository;
  private final AppointmentRepository appointmentRepository;
  private final InstallmentRepository installmentRepository;
  private final InventoryItemRepository inventoryItemRepository;
  private final LeadRepository leadRepository;
  private final PatientRepository patientRepository;
  private final PaymentPlanRepository paymentPlanRepository;

  public AssistantContextService(
      OpportunityRepository opportunityRepository,
      TreatmentPlanRepository treatmentPlanRepository,
      AppointmentRepository appointmentRepository,
      InstallmentRepository installmentRepository,
      InventoryItemRepository inventoryItemRepository,
      LeadRepository leadRepository,
      PatientRepository patientRepository,
      PaymentPlanRepository paymentPlanRepository) {
    this.opportunityRepository = opportunityRepository;
    this.treatmentPlanRepository = treatmentPlanRepository;
    this.appointmentRepository = appointmentRepository;
    this.installmentRepository = installmentRepository;
    this.inventoryItemRepository = inventoryItemRepository;
    this.leadRepository = leadRepository;
    this.patientRepository = patientRepository;
    this.paymentPlanRepository = paymentPlanRepository;
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
        .append(" (").append(o.getRelatedEntityType())
        .append(": ").append(nombreRelacionado(o, tenantId)).append(")")
        .append(" detectada ").append(fecha(o.getDetectedAt()))
        .append(", estado ").append(o.getStatus()).append(".\n"));

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
    sinDecidir.stream()
        .sorted(Comparator.comparing(
            TreatmentPlan::getTotalPriceCop,
            Comparator.nullsLast(Comparator.naturalOrder())).reversed())
        .limit(TOP)
        .forEach(p -> foto.append("- ").append(nombrePaciente(p.getPatient()))
            .append(" $").append(p.getTotalPriceCop())
            .append(" (").append(p.getStatus())
            .append(", presentado ").append(fecha(p.getCreatedAt())).append(").\n"));

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
            .append(" paciente: ").append(nombrePaciente(c.getPatient()))
            .append(", profesional: ").append(c.getProfessional() != null
                ? c.getProfessional().getFullName() : "—")
            .append(", consultorio: ").append(c.getRoom() != null
                ? c.getRoom().getName() : "—")
            .append(".\n"));

    // Cartera vencida: conteo + total + top por antigüedad para cobrar.
    List<Installment> vencidas = installmentRepository.findByStatus(InstallmentStatus.vencida)
        .stream()
        .sorted(Comparator.comparing(
            Installment::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
    BigDecimal totalVencido = vencidas.stream()
        .map(c -> c.getAmountCop() != null ? c.getAmountCop() : BigDecimal.ZERO)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    foto.append("Cuotas vencidas por cobrar: ").append(vencidas.size())
        .append(" por $").append(totalVencido).append(" COP en total.\n");
    LocalDate hoy = LocalDate.now(ZONA_CLINICA);
    vencidas.stream().limit(TOP).forEach(c -> foto.append("- $").append(c.getAmountCop())
        .append(" vence ").append(c.getDueDate())
        .append(" (hace ").append(diasMora(c.getDueDate(), hoy)).append(" días")
        .append(", paciente: ").append(nombrePacienteDeCuota(c, tenantId)).append(").\n"));

    // Inventario crítico.
    List<InventoryItem> criticos = inventoryItemRepository.findCritical(tenantId);
    foto.append("Insumos en nivel crítico: ").append(criticos.size()).append(".\n");
    criticos.stream().limit(TOP).forEach(i -> foto.append("- ").append(i.getName())
        .append(" (stock ").append(i.getQuantity())
        .append("/mínimo ").append(i.getMinThreshold()).append(").\n"));

    // Leads nuevos sin contactar: del más antiguo al más nuevo para poder
    // responder "quién lleva más tiempo esperando" sin inventar fechas.
    List<Lead> leadsNuevos = leadRepository.findByTenantIdAndStatus(tenantId, LeadStatus.nuevo)
        .stream()
        .sorted(Comparator.comparing(
            Lead::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
    foto.append("Leads nuevos sin contactar: ").append(leadsNuevos.size()).append(".\n");
    leadsNuevos.stream().limit(TOP).forEach(l -> foto.append("- ").append(l.getFullName())
        .append(" (creado ").append(fecha(l.getCreatedAt()))
        .append(", hace ").append(diasDesde(l.getCreatedAt(), ahora)).append(" días")
        .append(", último contacto: ")
        .append(l.getLastContactAt() != null ? fecha(l.getLastContactAt()) : "—")
        .append(interesDe(l)).append(").\n"));

    return foto.toString();
  }

  private static String nombrePaciente(Appointment cita) {
    return cita.getPatient() == null ? "—" : nombrePaciente(cita.getPatient());
  }

  /**
   * Nombre completo de un paciente para el snapshot ("—" si no hay).
   */
  private static String nombrePaciente(Patient paciente) {
    if (paciente == null) {
      return "—";
    }
    return (paciente.getFirstName() + " " + paciente.getLastName()).strip();
  }

  /**
   * Procedimiento de interés del lead como fragmento opcional de su línea.
   */
  private static String interesDe(Lead lead) {
    if (lead.getProcedureOfInterest() == null || lead.getProcedureOfInterest().isBlank()) {
      return "";
    }
    return ", interesa: " + lead.getProcedureOfInterest().strip();
  }

  /**
   * Nombre visible de la entidad origen de una oportunidad (lead, paciente o
   * insumo). Tipos desconocidos o ausentes → "—" sin romper el snapshot.
   */
  private String nombreRelacionado(Opportunity oportunidad, UUID tenantId) {
    String tipo = oportunidad.getRelatedEntityType();
    UUID id = oportunidad.getRelatedEntityId();
    if (tipo == null || id == null) {
      return "—";
    }
    return switch (tipo) {
      case "lead" -> leadRepository.findByIdAndTenantId(id, tenantId)
          .map(Lead::getFullName).orElse("—");
      case "patient" -> patientRepository.findByIdAndTenantId(id, tenantId)
          .map(AssistantContextService::nombrePaciente).orElse("—");
      case "treatment_plan" -> treatmentPlanRepository.findByIdAndTenantId(id, tenantId)
          .map(t -> nombrePaciente(t.getPatient())).orElse("—");
      case "appointment" -> appointmentRepository.findByIdAndTenantId(id, tenantId)
          .map(a -> nombrePaciente(a.getPatient())).orElse("—");
      case "installment" -> installmentRepository.findById(id)
          .map(cuota -> nombrePacienteDeCuota(cuota, tenantId)).orElse("—");
      case "inventory_item" -> inventoryItemRepository.findById(id)
          .map(InventoryItem::getName).orElse("—");
      default -> "—";
    };
  }

  /**
   * Paciente de una cuota navegando plan de pago → plan de tratamiento.
   * Cualquier eslabón ausente o de otro tenant → "—".
   */
  private String nombrePacienteDeCuota(Installment cuota, UUID tenantId) {
    if (cuota.getPaymentPlan() == null) {
      return "—";
    }
    return paymentPlanRepository.findById(cuota.getPaymentPlan().getId())
        .flatMap(plan -> treatmentPlanRepository.findByIdAndTenantId(
            plan.getTreatmentPlanId(), tenantId))
        .map(t -> nombrePaciente(t.getPatient()))
        .orElse("—");
  }

  /**
   * Fecha calendario de la clínica para un instante (texto legible por el modelo).
   */
  private static String fecha(Instant momento) {
    if (momento == null) {
      return "—";
    }
    return momento.atZone(ZONA_CLINICA).toLocalDate().toString();
  }

  /**
   * Días completos entre un instante y la referencia (nunca negativo).
   */
  private static long diasDesde(Instant momento, Instant ahora) {
    if (momento == null) {
      return 0;
    }
    return Math.max(0, Duration.between(momento, ahora).toDays());
  }

  /**
   * Días de mora de una cuota vencida respecto a hoy (nunca negativo).
   */
  private static long diasMora(LocalDate vencimiento, LocalDate hoy) {
    if (vencimiento == null) {
      return 0;
    }
    return Math.max(0, ChronoUnit.DAYS.between(vencimiento, hoy));
  }
}

