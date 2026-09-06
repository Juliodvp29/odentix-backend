package com.julio.odentix.odentix_backend.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.entity.TenantStatus;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Pruebas de integración para TenantRepository (FASE1-01) contra PostgreSQL
 * real y desechable vía Testcontainers.
 */
class TenantRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private TenantRepository tenantRepository;

  @Test
  void persistirYRecuperarTenant() {
    Tenant tenant = new Tenant("Clínica Dental Sonrisas", "900123456-1");

    Tenant guardado = tenantRepository.save(tenant);

    assertThat(guardado.getId()).isNotNull();
    assertThat(guardado.getName()).isEqualTo("Clínica Dental Sonrisas");
    assertThat(guardado.getTaxId()).isEqualTo("900123456-1");
    assertThat(guardado.getStatus()).isEqualTo(TenantStatus.trial);
    assertThat(guardado.getTimezone()).isEqualTo("America/Bogota");
    assertThat(guardado.getCreatedAt()).isNotNull();
    assertThat(guardado.getUpdatedAt()).isNotNull();

    Optional<Tenant> recuperado = tenantRepository.findById(guardado.getId());
    assertThat(recuperado).isPresent();
    assertThat(recuperado.get().getName()).isEqualTo("Clínica Dental Sonrisas");
  }

  @Test
  void actualizarEstadoTenant() {
    Tenant tenant = new Tenant("Clínica Sanitas Odonto", "901987654-2");
    Tenant guardado = tenantRepository.save(tenant);

    guardado.setStatus(TenantStatus.active);
    Tenant actualizado = tenantRepository.save(guardado);

    Optional<Tenant> recuperado = tenantRepository.findById(actualizado.getId());
    assertThat(recuperado).isPresent();
    assertThat(recuperado.get().getStatus()).isEqualTo(TenantStatus.active);
  }
}
