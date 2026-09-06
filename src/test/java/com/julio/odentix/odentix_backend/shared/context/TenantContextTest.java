package com.julio.odentix.odentix_backend.shared.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextTest {

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void setYGetTenantId() {
    UUID tenantId = UUID.randomUUID();
    TenantContext.setTenantId(tenantId);

    assertThat(TenantContext.getTenantId()).isEqualTo(tenantId);
    assertThat(TenantContext.getRequiredTenantId()).isEqualTo(tenantId);
  }

  @Test
  void clearEliminaTenantId() {
    TenantContext.setTenantId(UUID.randomUUID());
    TenantContext.clear();

    assertThat(TenantContext.getTenantId()).isNull();
    assertThatThrownBy(TenantContext::getRequiredTenantId)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No hay tenant_id configurado");
  }

  @Test
  void aislamientoEntreHilosIndependientes() throws InterruptedException {
    UUID tenantHiloPrincipal = UUID.randomUUID();
    UUID tenantHiloSecundario = UUID.randomUUID();

    TenantContext.setTenantId(tenantHiloPrincipal);

    AtomicReference<UUID> tenantEncontradoEnHiloSecundario = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    Thread hiloSecundario = new Thread(() -> {
      tenantEncontradoEnHiloSecundario.set(TenantContext.getTenantId());
      TenantContext.setTenantId(tenantHiloSecundario);
      latch.countDown();
    });

    hiloSecundario.start();
    boolean completed = latch.await(2, TimeUnit.SECONDS);

    assertThat(completed).isTrue();
    // El hilo secundario arrancó sin tenant heredado
    assertThat(tenantEncontradoEnHiloSecundario.get()).isNull();
    // El hilo principal mantiene su propio tenant intacto
    assertThat(TenantContext.getTenantId()).isEqualTo(tenantHiloPrincipal);
  }
}
