package com.julio.odentix.odentix_backend.shared.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.shared.entity.TestBusinessEntity;
import com.julio.odentix.odentix_backend.shared.repository.TestBusinessEntityRepository;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Test de integración para validar el filtrado automático de tenant_id en repositorios (FASE1-09).
 *
 * <p>Verifica que una query "ingenua" (sin especificar tenant_id en la consulta) filtre
 * automáticamente por el tenant configurado en {@link TenantContext} mediante {@code @TenantId}.
 */
class TenantFilterIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private TestBusinessEntityRepository testBusinessEntityRepository;

  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();
    testBusinessEntityRepository.deleteAll();

    tenantA = tenantRepository.save(new Tenant("Clínica Norte", "901111111-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Sur", "902222222-2"));
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void findAllConTenantActivoSoloDevuelveEntidadesDelTenantActivo() {
    // 1. Guardamos una entidad para Tenant A bajo su contexto
    TenantContext.setTenantId(tenantA.getId());
    TestBusinessEntity entityA = testBusinessEntityRepository.save(new TestBusinessEntity("Servicio A"));

    // 2. Guardamos una entidad para Tenant B bajo su contexto
    TenantContext.setTenantId(tenantB.getId());
    TestBusinessEntity entityB = testBusinessEntityRepository.save(new TestBusinessEntity("Servicio B"));

    // 3. Consultamos como Tenant A con findAll() ingenuo
    TenantContext.setTenantId(tenantA.getId());
    List<TestBusinessEntity> resultsA = testBusinessEntityRepository.findAll();

    assertThat(resultsA)
        .hasSize(1)
        .extracting(TestBusinessEntity::getName)
        .containsExactly("Servicio A");
    assertThat(resultsA.getFirst().getTenantId()).isEqualTo(tenantA.getId());

    // 4. Consultamos como Tenant B con findAll() ingenuo
    TenantContext.setTenantId(tenantB.getId());
    List<TestBusinessEntity> resultsB = testBusinessEntityRepository.findAll();

    assertThat(resultsB)
        .hasSize(1)
        .extracting(TestBusinessEntity::getName)
        .containsExactly("Servicio B");
    assertThat(resultsB.getFirst().getTenantId()).isEqualTo(tenantB.getId());
  }

  @Test
  void findByIdConTenantActivoNoDevuelveRegistroDeOtroTenant() {
    // Guardamos una entidad para Tenant B
    TenantContext.setTenantId(tenantB.getId());
    TestBusinessEntity entityB = testBusinessEntityRepository.save(new TestBusinessEntity("Tratamiento B"));

    // Como Tenant A intentamos buscar por ID directo el registro del Tenant B
    TenantContext.setTenantId(tenantA.getId());
    Optional<TestBusinessEntity> found = testBusinessEntityRepository.findById(entityB.getId());

    // Debe ser empty porque Hibernate @TenantId añade AND tenant_id = :currentTenant
    assertThat(found).isEmpty();
  }

  @Test
  void findByNameJpqlInyectaFiltroDeTenantAutomaticamente() {
    // Creamos una entidad con el mismo nombre en ambos tenants
    TenantContext.setTenantId(tenantA.getId());
    testBusinessEntityRepository.save(new TestBusinessEntity("Profilaxis"));

    TenantContext.setTenantId(tenantB.getId());
    testBusinessEntityRepository.save(new TestBusinessEntity("Profilaxis"));

    // Consultamos vía JPQL ingenua ("SELECT t FROM TestBusinessEntity t WHERE t.name = :name")
    TenantContext.setTenantId(tenantA.getId());
    List<TestBusinessEntity> resultsA = testBusinessEntityRepository.findByNameJpql("Profilaxis");

    assertThat(resultsA).hasSize(1);
    assertThat(resultsA.getFirst().getTenantId()).isEqualTo(tenantA.getId());
  }

  @Test
  void saveSinSetearTenantIdAsignaTenantDelContextoAutomaticamente() {
    TenantContext.setTenantId(tenantA.getId());

    TestBusinessEntity entity = new TestBusinessEntity("Ortodoncia");
    assertThat(entity.getTenantId()).isNull();

    TestBusinessEntity saved = testBusinessEntityRepository.save(entity);

    assertThat(saved.getTenantId()).isEqualTo(tenantA.getId());
  }
}
