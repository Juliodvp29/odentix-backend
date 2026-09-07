package com.julio.odentix.odentix_backend.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.audit.entity.AuditAction;
import com.julio.odentix.odentix_backend.audit.entity.AuditLog;
import com.julio.odentix.odentix_backend.audit.repository.AuditRepository;
import com.julio.odentix.odentix_backend.audit.service.AuditService;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de integración para AuditService (FASE1-13) con Testcontainers.
 *
 * <p>Verifica registro desde código, consulta filtrada por tenant y
 * aislamiento cross-tenant con query ingenua (filtro @TenantId automático).
 */
class AuditServiceIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private AuditService auditService;

  @Autowired
  private AuditRepository auditRepository;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private UserService userService;

  private Tenant tenantA;
  private Tenant tenantB;
  private User userA;

  @BeforeEach
  void setUp() {
    tenantA = tenantRepository.save(new Tenant("Clínica A", "900111111-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica B", "900222222-2"));
    userA = userService.createUser(
        tenantA.getId(), "admin@clinicaa.com", "Secreta123", "Admin A", UserRole.propietario);
  }

  @Test
  void registrarYConsultarFiltradoPorTenant() {
    auditService.log(
        tenantA.getId(),
        userA.getId(),
        AuditAction.insert,
        "users",
        userA.getId(),
        Map.of("email", "admin@clinicaa.com"));
    auditService.log(tenantA.getId(), null, AuditAction.login_failed, "users", null, null);

    List<AuditLog> entradas = auditRepository.findAllByTenantIdOrderByCreatedAtDesc(tenantA.getId());

    assertThat(entradas).hasSize(2);
    assertThat(entradas)
        .allSatisfy(e -> assertThat(e.getTenantId()).isEqualTo(tenantA.getId()));
    assertThat(entradas)
        .anySatisfy(e -> {
          assertThat(e.getAction()).isEqualTo(AuditAction.insert);
          assertThat(e.getUserId()).isEqualTo(userA.getId());
          assertThat(e.getDetail()).containsEntry("email", "admin@clinicaa.com");
          assertThat(e.getCreatedAt()).isNotNull();
        });
  }

  @Test
  void queryIngenuaSoloVeEntradasDelTenantActivo() {
    auditService.log(tenantA.getId(), null, AuditAction.login_success, "users", userA.getId(), null);
    auditService.log(tenantB.getId(), null, AuditAction.login_success, "users", null, null);

    TenantContext.setTenantId(tenantA.getId());
    try {
      List<AuditLog> visibles = auditRepository.findAll();
      assertThat(visibles).hasSize(1);
      assertThat(visibles.get(0).getTenantId()).isEqualTo(tenantA.getId());
    } finally {
      TenantContext.clear();
    }
  }
}
