package com.julio.odentix.odentix_backend.tenant.service;

import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.shared.crypto.DataEncryptionService;
import com.julio.odentix.odentix_backend.shared.exception.ResourceNotFoundException;
import com.julio.odentix.odentix_backend.tenant.dto.TenantSettingsResponse;
import com.julio.odentix.odentix_backend.tenant.dto.UpdateTenantSettingsRequest;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ajustes de la propia clínica (pre-Fase 12).
 *
 * <p>Siempre sobre el tenant del contexto —el endpoint nunca acepta ID por
 * parámetro, así que una clínica no puede editar la configuración de otra
 * (regla §5 de AGENTS.md). Solo el remitente de notificaciones es editable;
 * el resto de la ficha (nombre, NIT, plan) sigue siendo administrativo.
 */
@Service
public class TenantService {

  private final TenantRepository tenantRepository;
  private final DataEncryptionService encryptionService;

  public TenantService(
      TenantRepository tenantRepository, DataEncryptionService encryptionService) {
    this.tenantRepository = tenantRepository;
    this.encryptionService = encryptionService;
  }

  /**
   * Ajustes actuales de la clínica autenticada.
   */
  @Transactional(readOnly = true)
  public TenantSettingsResponse obtenerAjustes() {
    return TenantSettingsResponse.fromEntity(buscarPropio());
  }

  /**
   * Actualiza el remitente de notificaciones. Email vacío/nulo lo limpia y
   * vuelve al remitente global (con `Reply-To` a la clínica solo si hay
   * correo propio — ver `SmtpEmailNotificationSender`).
   *
   * <p>Nota operativa: el dominio debe estar verificado en el proveedor SMTP
   * (Resend: Domains); si no, los envíos quedan `fallida` con el detalle.
   */
  @Transactional
  public TenantSettingsResponse actualizarAjustes(UpdateTenantSettingsRequest request) {
    Tenant tenant = buscarPropio();

    String email = request.getNotificationEmail();
    if (email != null && !email.isBlank()) {
      String limpio = email.strip();
      if (!limpio.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
        throw new IllegalArgumentException("notificationEmail tiene un formato inválido.");
      }
      tenant.setNotificationEmail(limpio);
    } else {
      tenant.setNotificationEmail(null);
    }
    String nombre = request.getNotificationName();
    tenant.setNotificationName(nombre != null && !nombre.isBlank() ? nombre.strip() : null);

    // WhatsApp propio: número en claro (no es secreto), token cifrado
    // (write-only: vacío lo deja intacto para no borrarlo sin querer).
    String numero = request.getWhatsappPhoneNumberId();
    if (numero != null) {
      tenant.setWhatsappPhoneNumberId(
          !numero.isBlank() ? numero.strip() : null);
    }
    String token = request.getWhatsappToken();
    if (token != null && !token.isBlank()) {
      tenant.setWhatsappTokenCifrado(encryptionService.cifrar(token.strip()));
    }

    return TenantSettingsResponse.fromEntity(tenantRepository.save(tenant));
  }

  private Tenant buscarPropio() {
    UUID tenantId = TenantContext.getRequiredTenantId();
    return tenantRepository.findById(tenantId)
        .orElseThrow(() -> new ResourceNotFoundException(
            "Clínica no encontrada: " + tenantId));
  }
}
