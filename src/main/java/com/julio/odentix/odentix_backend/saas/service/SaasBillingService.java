package com.julio.odentix.odentix_backend.saas.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.julio.odentix.odentix_backend.saas.client.BoldClient;
import com.julio.odentix.odentix_backend.saas.dto.CheckoutRequest;
import com.julio.odentix.odentix_backend.saas.dto.CheckoutResponse;
import com.julio.odentix.odentix_backend.saas.entity.SaasPayment;
import com.julio.odentix.odentix_backend.saas.entity.SaasPaymentStatus;
import com.julio.odentix.odentix_backend.saas.repository.SaasPaymentRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.subscription.entity.BillingCycle;
import com.julio.odentix.odentix_backend.subscription.entity.Plan;
import com.julio.odentix.odentix_backend.subscription.entity.SubscriptionStatus;
import com.julio.odentix.odentix_backend.subscription.entity.TenantSubscription;
import com.julio.odentix.odentix_backend.subscription.repository.PlanRepository;
import com.julio.odentix.odentix_backend.subscription.repository.TenantSubscriptionRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facturación del propio SaaS con Bold (FASE11-04).
 *
 * <p>Modelo (Bold.co no tiene recurrencia nativa): cada ciclo se cobra con un
 * link de pago que la clínica paga; el webhook confirma y extiende el periodo.
 * Sin link pagado no hay extensión; con 7 días de gracia vencidos la
 * suscripción se cancela (ver `SaasRenewalJob`).
 *
 * <p>Webhook (verificado en `developers.bold.co`): CloudEvents con `type`
 * (`SALE_APPROVED/_REJECTED`, `VOID_*`), firma
 * `hex(HMAC-SHA256(Base64(rawBody), secret))` contra `x-bold-signature`,
 * respuesta 200 inmediata e idempotencia por notification `id`. En sandbox la
 * firma usa clave vacía —igual que aquí cuando el secret no está configurado.
 */
@Service
public class SaasBillingService {

  private static final Logger log = LoggerFactory.getLogger(SaasBillingService.class);

  /** Estados de suscripción considerados "vivos" para buscar la actual. */
  private static final List<SubscriptionStatus> ESTADOS_VIVOS = List.of(
      SubscriptionStatus.trialing,
      SubscriptionStatus.active,
      SubscriptionStatus.past_due);

  private final BoldClient boldClient;
  private final SaasPaymentRepository paymentRepository;
  private final TenantSubscriptionRepository subscriptionRepository;
  private final PlanRepository planRepository;
  private final ObjectMapper objectMapper;
  private final String webhookSecret;

  public SaasBillingService(
      BoldClient boldClient,
      SaasPaymentRepository paymentRepository,
      TenantSubscriptionRepository subscriptionRepository,
      PlanRepository planRepository,
      @Value("${odentix.saas.bold.webhook-secret:}") String webhookSecret) {
    this.boldClient = boldClient;
    this.paymentRepository = paymentRepository;
    this.subscriptionRepository = subscriptionRepository;
    this.planRepository = planRepository;
    // Sin bean ObjectMapper en el contexto: instancia propia (thread-safe para lectura).
    this.objectMapper = new ObjectMapper();
    this.webhookSecret = webhookSecret != null ? webhookSecret : "";
  }

  /**
   * Inicia el checkout: fija plan+ciclo y genera el link de pago de Bold.
   *
   * <p>Idempotente por ciclo: si ya hay un link pendiente para la suscripción,
   * devuelve su URL en vez de crear otro.
   *
   * @return URL de pago de Bold y datos de la suscripción.
   */
  @Transactional
  public CheckoutResponse checkout(CheckoutRequest request) {
    UUID tenantId = TenantContext.getRequiredTenantId();

    Plan plan = planRepository.findByCode(request.getPlanCode())
        .orElseThrow(() -> new ResourceNotFoundException(
            "Plan no encontrado: " + request.getPlanCode()));
    BillingCycle cycle = request.getBillingCycle() != null
        ? request.getBillingCycle()
        : BillingCycle.monthly;
    BigDecimal monto = cycle == BillingCycle.annual
        ? plan.getAnnualPriceCop()
        : plan.getMonthlyPriceCop();

    TenantSubscription suscripcion = subscriptionRepository
        .findLiveByTenantId(tenantId, ESTADOS_VIVOS)
        .orElse(null);
    if (suscripcion == null) {
      suscripcion = new TenantSubscription(tenantId, plan, extender(Instant.now(), cycle));
      suscripcion.setBillingCycle(cycle);
      suscripcion = subscriptionRepository.save(suscripcion);
    } else {
      suscripcion.setPlan(plan);
      suscripcion.setBillingCycle(cycle);
      if (suscripcion.getStatus() == SubscriptionStatus.cancelled) {
        suscripcion.setStatus(SubscriptionStatus.trialing);
      }
    }

    List<SaasPayment> pendientes = paymentRepository.findBySubscriptionIdAndStatus(
        suscripcion.getId(), SaasPaymentStatus.pendiente);
    if (!pendientes.isEmpty() && pendientes.get(0).getBoldPaymentLink() != null) {
      SaasPayment existente = pendientes.get(0);
      return respuestaCheckout(suscripcion, existente);
    }

    String referencia = "ODX-" + tenantId.toString().replace("-", "").substring(0, 8)
        + "-" + System.currentTimeMillis();
    SaasPayment pago = new SaasPayment(
        tenantId, suscripcion, referencia, monto, cycle);
    pago = paymentRepository.save(pago);

    long expiracionNanos = suscripcion.getCurrentPeriodEnd()
        .plusSeconds(7 * 24 * 3600).toEpochMilli() * 1_000_000L;
    BoldClient.PaymentLink link = boldClient.createPaymentLink(
        monto.longValue(),
        referencia,
        "Suscripción Odentix (" + plan.getCode() + "/" + cycle.name() + ")",
        request.getPayerEmail(),
        expiracionNanos);
    pago.setBoldPaymentLink(link.url());
    pago = paymentRepository.save(pago);

    return respuestaCheckout(suscripcion, pago);
  }

  /**
   * Procesa un evento de webhook de Bold (firma ya verificada por el controller
   * o verificada aquí si se llama directo).
   *
   * @param notificationId `id` CloudEvents (idempotencia).
   * @param type `SALE_APPROVED`, `SALE_REJECTED`, `VOID_APPROVED`, etc.
   * @param reference `data.metadata.reference` (nuestra referencia).
   * @return true si se aplicó algún cambio, false si se ignoró/duplicó.
   */
  @Transactional
  public boolean aplicarEvento(String notificationId, String type, String reference) {
    if (notificationId != null
        && paymentRepository.existsByBoldNotificationId(notificationId)) {
      log.info("Webhook Bold duplicado {}: ya procesado, se confirma 200.", notificationId);
      return false;
    }

    SaasPayment pago = reference != null
        ? paymentRepository.findByBoldReference(reference).orElse(null)
        : null;
    if (pago == null) {
      log.warn("Webhook Bold con referencia desconocida: {}.", reference);
      return false;
    }
    pago.setBoldNotificationId(notificationId);
    TenantSubscription suscripcion = pago.getSubscription();

    switch (type) {
      case "SALE_APPROVED" -> {
        pago.setStatus(SaasPaymentStatus.pagada);
        pago.setPaidAt(Instant.now());
        suscripcion.setStatus(SubscriptionStatus.active);
        suscripcion.setCurrentPeriodStart(Instant.now());
        suscripcion.setCurrentPeriodEnd(extender(Instant.now(), pago.getBillingCycle()));
        log.info("Suscripción {} activada hasta {}.",
            suscripcion.getId(), suscripcion.getCurrentPeriodEnd());
      }
      case "SALE_REJECTED" -> {
        pago.setStatus(SaasPaymentStatus.rechazada);
        suscripcion.setStatus(SubscriptionStatus.past_due);
        log.info("Pago {} rechazado: suscripción {} en mora.", pago.getId(), suscripcion.getId());
      }
      case "VOID_APPROVED" -> {
        pago.setStatus(SaasPaymentStatus.rechazada);
        suscripcion.setStatus(SubscriptionStatus.past_due);
        log.info("Pago {} anulado: suscripción {} en mora.", pago.getId(), suscripcion.getId());
      }
      default ->
        log.info("Evento Bold {} ignorado para el pago {}.", type, pago.getId());
    }
    return true;
  }

  /**
   * Verifica la firma HMAC del webhook según `developers.bold.co`: Base64 del
   * cuerpo crudo → HMAC-SHA256 hexadecimal con el secret → comparar con
   * `x-bold-signature` en tiempo constante.
   *
   * @return true si la firma es válida.
   */
  public boolean firmaValida(String rawBody, String signature) {
    if (signature == null || signature.isBlank()) {
      return false;
    }
    try {
      String base64 = Base64.getEncoder()
          .encodeToString(rawBody.getBytes(StandardCharsets.UTF_8));
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(
          webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      StringBuilder hex = new StringBuilder();
      for (byte b : mac.doFinal(base64.getBytes(StandardCharsets.UTF_8))) {
        hex.append(String.format("%02x", b));
      }
      return MessageDigest.isEqual(
          hex.toString().getBytes(StandardCharsets.UTF_8),
          signature.strip().getBytes(StandardCharsets.UTF_8));
    } catch (RuntimeException | java.security.NoSuchAlgorithmException
        | java.security.InvalidKeyException e) {
      return false;
    }
  }

  /**
   * Extrae `(notificationId, type, reference)` del JSON CloudEvents de Bold.
   */
  public String[] parsearEvento(String rawBody) {
    try {
      JsonNode root = objectMapper.readTree(rawBody);
      String id = root.path("id").asText(null);
      String type = root.path("type").asText(null);
      JsonNode data = root.path("data");
      String reference = null;
      if (data.has("metadata")) {
        reference = data.path("metadata").path("reference").asText(null);
      }
      // Fallback: algunos eventos traen la referencia del link en metadata o
      // el subject es el payment_id; la correlación primaria es reference.
      return new String[]{id, type, reference};
    } catch (RuntimeException | java.io.IOException e) {
      throw new IllegalArgumentException("Cuerpo del webhook inválido.");
    }
  }

  static Instant extender(Instant desde, BillingCycle cycle) {
    // Instant no soporta MONTHS: meses calendario vía OffsetDateTime.
    return OffsetDateTime.ofInstant(desde, ZoneOffset.UTC)
        .plusMonths(cycle == BillingCycle.annual ? 12 : 1)
        .toInstant();
  }

  private CheckoutResponse respuestaCheckout(TenantSubscription sub, SaasPayment pago) {
    CheckoutResponse response = new CheckoutResponse();
    response.setSubscriptionId(sub.getId());
    response.setPlanCode(sub.getPlan().getCode());
    response.setBillingCycle(sub.getBillingCycle());
    response.setAmountCop(pago.getAmountCop());
    response.setPaymentUrl(pago.getBoldPaymentLink());
    response.setStatus(sub.getStatus());
    return response;
  }
}
