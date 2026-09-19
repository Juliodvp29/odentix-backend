package com.julio.odentix.odentix_backend.opportunity.service;

import com.julio.odentix.odentix_backend.appointment.entity.Appointment;
import com.julio.odentix.odentix_backend.billing.entity.Installment;
import com.julio.odentix.odentix_backend.crm.entity.Lead;
import com.julio.odentix.odentix_backend.inventory.entity.InventoryItem;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityAction;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityActionType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityActionRepository;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.treatmentplan.entity.TreatmentPlan;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Construye las acciones sugeridas de cada oportunidad (FASE9-03: "proponer").
 *
 * <p>Cada regla de detección genera, junto a la oportunidad:
 * <ul>
 *   <li>Una acción {@code crear_tarea} (siempre): con título y descripción
 *       contextual para que recepción sepa qué hacer.</li>
 *   <li>Una acción {@code enviar_mensaje} (solo si hay destinatario directo):
 *       con el canal resuelto (teléfono → WhatsApp, si no email) y el mensaje
 *       congelado en `suggested_message`. Al ejecutar se resuelve el
 *       destinatario fresco; si ya no hay contacto, la ejecución falla con
 *       409 en vez de enviar a un dato viejo.</li>
 * </ul>
 *
 * <p>Los jobs llaman al método de su entidad ya cargada (sin recargas extra).
 */
@Service
public class OpportunityActionFactory {

  private final OpportunityActionRepository actionRepository;

  public OpportunityActionFactory(OpportunityActionRepository actionRepository) {
    this.actionRepository = actionRepository;
  }

  /** Sugerencias para un lead sin respuesta. */
  @Transactional
  public List<OpportunityAction> paraLead(Opportunity oportunidad, Lead lead) {
    List<OpportunityAction> acciones = new ArrayList<>();
    acciones.add(tarea(oportunidad,
        "Contactar lead " + lead.getFullName(),
        "Lead sin respuesta (tel: " + dato(lead.getPhone()) + ", email: "
            + dato(lead.getEmail()) + "). Llamar o escribir para calificar."));
    mensajePara(oportunidad, lead.getPhone(), lead.getEmail(),
        "Hola " + lead.getFullName() + ", vimos tu interés"
            + (lead.getProcedureOfInterest() != null ? " en " + lead.getProcedureOfInterest() : "")
            + ". ¿Te agendamos una valoración? Responde a este mensaje.")
        .ifPresent(acciones::add);
    return acciones;
  }

  /** Sugerencias para una cita de alto riesgo. */
  @Transactional
  public List<OpportunityAction> paraAppointment(Opportunity oportunidad, Appointment cita) {
    List<OpportunityAction> acciones = new ArrayList<>();
    Patient paciente = cita.getPatient();
    acciones.add(tarea(oportunidad,
        "Confirmar cita de " + nombre(paciente),
        "Cita de alto riesgo el " + cita.getStartsAt() + ". Confirmar asistencia o "
            + "reprogramar para no perder el espacio."));
    mensajePara(oportunidad, telefono(paciente), email(paciente),
        "Hola " + nombre(paciente) + ", te recordamos tu cita del " + cita.getStartsAt()
            + ". Confirma tu asistencia o pide reprogramar respondiendo aquí.")
        .ifPresent(acciones::add);
    return acciones;
  }

  /** Sugerencias para un tratamiento sin seguimiento. */
  @Transactional
  public List<OpportunityAction> paraTreatmentPlan(Opportunity oportunidad, TreatmentPlan plan) {
    List<OpportunityAction> acciones = new ArrayList<>();
    Patient paciente = plan.getPatient();
    acciones.add(tarea(oportunidad,
        "Retomar tratamiento de " + nombre(paciente),
        "Plan presentado sin decisión. Llamar para resolver dudas y cerrar la aceptación."));
    mensajePara(oportunidad, telefono(paciente), email(paciente),
        "Hola " + nombre(paciente) + ", tu plan de tratamiento sigue disponible. "
            + "¿Retomamos? Agenda tu cita respondiendo aquí.")
        .ifPresent(acciones::add);
    return acciones;
  }

  /** Sugerencias para un paciente inactivo. */
  @Transactional
  public List<OpportunityAction> paraPatient(Opportunity oportunidad, Patient paciente) {
    List<OpportunityAction> acciones = new ArrayList<>();
    acciones.add(tarea(oportunidad,
        "Recuperar paciente " + nombre(paciente),
        "Sin citas ni tratamientos recientes. Invitar a control preventivo."));
    mensajePara(oportunidad, telefono(paciente), email(paciente),
        "Hola " + nombre(paciente) + ", hace tiempo no te vemos por la clínica. "
            + "¿Agendamos tu control?")
        .ifPresent(acciones::add);
    return acciones;
  }

  /** Sugerencias para una cuota vencida (paciente resuelto por el job). */
  @Transactional
  public List<OpportunityAction> paraInstallment(
      Opportunity oportunidad, Installment cuota, Patient paciente) {
    List<OpportunityAction> acciones = new ArrayList<>();
    acciones.add(tarea(oportunidad,
        "Cobrar saldo de " + nombre(paciente),
        "Cuota vencida por $" + cuota.getAmountCop() + " COP. Gestionar cobro."));
    mensajePara(oportunidad, telefono(paciente), email(paciente),
        "Hola " + nombre(paciente) + ", te recordamos tu saldo pendiente de $"
            + cuota.getAmountCop() + " COP. Puedes pagar en clínica o pedir tu link de pago.")
        .ifPresent(acciones::add);
    return acciones;
  }

  /** Sugerencias para un ítem crítico (sin destinatario: solo tarea). */
  @Transactional
  public List<OpportunityAction> paraInventoryItem(Opportunity oportunidad, InventoryItem item) {
    List<OpportunityAction> acciones = new ArrayList<>();
    acciones.add(tarea(oportunidad,
        "Reponer " + item.getName(),
        "Stock en " + item.getQuantity() + " (mínimo " + item.getMinThreshold()
            + "). Generar orden de compra."));
    return acciones;
  }

  /** Sugerencias para un espacio disponible (sin destinatario único: solo tarea). */
  @Transactional
  public List<OpportunityAction> paraEspacio(Opportunity oportunidad, Appointment cita) {
    List<OpportunityAction> acciones = new ArrayList<>();
    acciones.add(tarea(oportunidad,
        "Reasignar espacio del " + cita.getStartsAt(),
        "Cita cancelada con candidatos en lista de espera (valor estimado $"
            + oportunidad.getEstimatedValueCop() + " COP). Contactarlos para reagendar."));
    return acciones;
  }

  // ---------------------------------------------------------------------------
  // Helpers privados
  // ---------------------------------------------------------------------------

  private OpportunityAction tarea(Opportunity oportunidad, String titulo, String descripcion) {
    // El título va dentro del mensaje sugerido: el esquema solo trae
    // suggested_message (sin columna de título) y al ejecutar se separa.
    OpportunityAction accion = new OpportunityAction(
        oportunidad.getTenantId(), oportunidad, OpportunityActionType.crear_tarea);
    accion.setSuggestedMessage(titulo + "\n" + descripcion);
    return actionRepository.save(accion);
  }

  private java.util.Optional<OpportunityAction> mensajePara(
      Opportunity oportunidad, String telefono, String email, String mensaje) {
    String canal = canalPara(telefono, email);
    if (canal == null) {
      return java.util.Optional.empty();
    }
    OpportunityAction accion = new OpportunityAction(
        oportunidad.getTenantId(), oportunidad, OpportunityActionType.enviar_mensaje);
    accion.setChannel(canal);
    accion.setSuggestedMessage(mensaje);
    return java.util.Optional.of(actionRepository.save(accion));
  }

  private static String canalPara(String telefono, String email) {
    if (telefono != null && !telefono.isBlank()) {
      return "whatsapp";
    }
    if (email != null && !email.isBlank()) {
      return "email";
    }
    return null;
  }

  private static String nombre(Patient paciente) {
    if (paciente == null) {
      return "paciente";
    }
    return (paciente.getFirstName() + " " + paciente.getLastName()).strip();
  }

  private static String telefono(Patient paciente) {
    return paciente != null ? paciente.getPhone() : null;
  }

  private static String email(Patient paciente) {
    return paciente != null ? paciente.getEmail() : null;
  }

  private static String dato(String valor) {
    return valor != null && !valor.isBlank() ? valor : "—";
  }
}
