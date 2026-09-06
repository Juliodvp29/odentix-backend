package com.julio.odentix.odentix_backend.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.julio.odentix.odentix_backend.shared.entity.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.lang.reflect.Modifier;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Test de convención (FASE1-04): la clase base existe, es abstracta y declara
 * el campo tenant_id con el mapeo exigido. Es la red de seguridad mínima para
 * que nadie rompa la base que usarán las entidades desde la Fase 2.
 */
class TenantAwareEntityTest {

  @Test
  void baseEsAbstractaYMappedSuperclass() {
    assertThat(Modifier.isAbstract(TenantAwareEntity.class.getModifiers())).isTrue();
    assertThat(TenantAwareEntity.class.isAnnotationPresent(MappedSuperclass.class)).isTrue();
  }

  @Test
  void tenantIdMapeaColumnaTenantIdNoNulaEInmutable() throws Exception {
    var campo = TenantAwareEntity.class.getDeclaredField("tenantId");
    assertThat(campo.getType()).isEqualTo(UUID.class);

    Column columna = campo.getAnnotation(Column.class);
    assertThat(columna).isNotNull();
    assertThat(columna.name()).isEqualTo("tenant_id");
    assertThat(columna.nullable()).isFalse();
    assertThat(columna.updatable()).isFalse();

    assertThat(campo.isAnnotationPresent(org.hibernate.annotations.TenantId.class)).isTrue();
  }

  @Test
  void baseDeclaraIdYTimestamps() throws Exception {
    assertThat(TenantAwareEntity.class.getDeclaredField("id").getType()).isEqualTo(UUID.class);
    assertThat(TenantAwareEntity.class.getDeclaredField("createdAt").getAnnotation(Column.class).name())
        .isEqualTo("created_at");
    assertThat(TenantAwareEntity.class.getDeclaredField("updatedAt").getAnnotation(Column.class).name())
        .isEqualTo("updated_at");
  }
}
