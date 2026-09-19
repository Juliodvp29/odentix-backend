package com.julio.odentix.odentix_backend.opportunity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.auth.entity.User;
import com.julio.odentix.odentix_backend.auth.entity.UserRole;
import com.julio.odentix.odentix_backend.auth.service.JwtService;
import com.julio.odentix.odentix_backend.auth.service.UserService;
import com.julio.odentix.odentix_backend.opportunity.entity.Opportunity;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityStatus;
import com.julio.odentix.odentix_backend.opportunity.entity.OpportunityType;
import com.julio.odentix.odentix_backend.opportunity.repository.OpportunityRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pruebas de integración HTTP para el endpoint de oportunidades (FASE9-01)
 * contra PostgreSQL real vía Testcontainers.
 *
 * <p>Cubre:
 * <ul>
 *   <li>Listado ordenado por prioridad DESC.</li>
 *   <li>Filtrado por estado.</li>
 *   <li>Aislamiento cross-tenant: Tenant B no ve oportunidades de Tenant A.</li>
 *   <li>Control de acceso por rol (sin token → 401).</li>
 * </ul>
 */
@AutoConfigureMockMvc
class OpportunityIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private TenantRepository tenantRepository;

  @Autowired
  private OpportunityRepository opportunityRepository;

  @Autowired
  private UserService userService;

  @Autowired
  private JwtService jwtService;

  private Tenant tenantA;
  private Tenant tenantB;
  private String tokenPropietarioA;
  private String tokenPropietarioB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica REST Oport Alfa", "992111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica REST Oport Beta", "993333444-2"));

    User propietarioA = userService.createUser(
        tenantA.getId(), "propietario@oport-alfa.com", "ClaveSegura123!", "Dr. Alfa",
        UserRole.propietario);
    tokenPropietarioA = jwtService.generateToken(propietarioA);

    User propietarioB = userService.createUser(
        tenantB.getId(), "propietario@oport-beta.com", "ClaveSegura456!", "Dr. Beta",
        UserRole.propietario);
    tokenPropietarioB = jwtService.generateToken(propietarioB);

    // Crear oportunidades directamente en BD para Tenant A
    TenantContext.setTenantId(tenantA.getId());
    crearOportunidad(tenantA.getId(), OpportunityType.tratamiento_sin_seguimiento,
        4, OpportunityStatus.abierta, new BigDecimal("2500000.00"));
    crearOportunidad(tenantA.getId(), OpportunityType.tratamiento_sin_seguimiento,
        2, OpportunityStatus.abierta, new BigDecimal("100000.00"));
    crearOportunidad(tenantA.getId(), OpportunityType.tratamiento_sin_seguimiento,
        3, OpportunityStatus.resuelta, new BigDecimal("800000.00"));
    TenantContext.clear();

    // Crear oportunidad para Tenant B
    TenantContext.setTenantId(tenantB.getId());
    crearOportunidad(tenantB.getId(), OpportunityType.tratamiento_sin_seguimiento,
        5, OpportunityStatus.abierta, new BigDecimal("5000000.00"));
    TenantContext.clear();
  }

  /**
   * GET sin filtro devuelve todas las oportunidades del tenant, ordenadas
   * por prioridad DESC.
   */
  @Test
  void listarTodasLasOportunidadesDelTenant() throws Exception {
    mockMvc.perform(get("/api/v1/opportunities")
            .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3)) // 3 de Tenant A (2 abiertas + 1 resuelta)
        .andExpect(jsonPath("$[0].priority").value(4))  // la de mayor prioridad primero
        .andExpect(jsonPath("$[1].priority").value(3))
        .andExpect(jsonPath("$[2].priority").value(2));
  }

  /**
   * GET con filtro por estado 'abierta' solo devuelve las abiertas.
   */
  @Test
  void filtrarPorEstadoAbierta() throws Exception {
    mockMvc.perform(get("/api/v1/opportunities")
            .param("status", "abierta")
            .header("Authorization", "Bearer " + tokenPropietarioA))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2)) // solo 2 abiertas en Tenant A
        .andExpect(jsonPath("$[0].status").value("abierta"))
        .andExpect(jsonPath("$[1].status").value("abierta"));
  }

  /**
   * Aislamiento cross-tenant: Tenant B solo ve su propia oportunidad.
   */
  @Test
  void aislamientoCrossTenant_tenantBNoVeOportunidadesDeA() throws Exception {
    mockMvc.perform(get("/api/v1/opportunities")
            .header("Authorization", "Bearer " + tokenPropietarioB))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].priority").value(5))
        .andExpect(jsonPath("$[0].estimatedValueCop").value(5000000.00));
  }

  /**
   * Sin token → 401 Unauthorized.
   */
  @Test
  void sinToken_devuelve401() throws Exception {
    mockMvc.perform(get("/api/v1/opportunities"))
        .andExpect(status().isUnauthorized());
  }

  // --- Helpers ---

  private Opportunity crearOportunidad(
      UUID tenantId, OpportunityType type, int priority,
      OpportunityStatus status, BigDecimal estimatedValue) {
    Opportunity op = new Opportunity(tenantId, type, (short) priority);
    op.setRelatedEntityType("treatment_plan");
    op.setRelatedEntityId(UUID.randomUUID());
    op.setEstimatedValueCop(estimatedValue);
    op.setStatus(status);
    return opportunityRepository.saveAndFlush(op);
  }
}
