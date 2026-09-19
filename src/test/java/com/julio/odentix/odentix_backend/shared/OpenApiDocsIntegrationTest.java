package com.julio.odentix.odentix_backend.shared;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifica que la documentación OpenAPI se genera y es pública en entornos
 * no productivos (en prod va deshabilitada vía application-prod.yml).
 *
 * <p>Lista exhaustiva de operaciones (regla §4 de AGENTS.md): si un
 * endpoint existe pero no sale en `/v3/api-docs`, este test falla.
 * Al agregar un endpoint, agregar aquí su path + método.
 */
@AutoConfigureMockMvc
class OpenApiDocsIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void apiDocsRespondePublicamenteConOperaciones() throws Exception {
    mockMvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.openapi").exists())
        // auth: login, refresh, logout.
        .andExpect(jsonPath("$.paths./api/v1/auth/login.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/auth/refresh.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/auth/logout.post").exists())
        // sesión.
        .andExpect(jsonPath("$.paths./api/v1/me.get").exists())
        // pacientes: CRUD, archivos, historia clínica y odontograma.
        .andExpect(jsonPath("$.paths./api/v1/patients.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/patients.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/patients/{id}.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/patients/{id}.patch").exists())
        .andExpect(jsonPath("$.paths./api/v1/patients/{id}.delete").exists())
        .andExpect(jsonPath("$.paths./api/v1/patients/{id}/files.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/patients/{id}/files.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/patients/{id}/files/{fileId}/download-url.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/patients/{id}/clinical-records.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/patients/{id}/clinical-records.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/patients/{id}/odontogram.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/patients/{id}/odontogram.get").exists())
        // agenda: crear, consultar, estado, valor y candidatos.
        .andExpect(jsonPath("$.paths./api/v1/appointments.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/appointments.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/appointments/{id}/status.patch").exists())
        .andExpect(jsonPath("$.paths./api/v1/appointments/estimated-value.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/appointments/{id}/waitlist-candidates.get").exists())
        // lista de espera.
        .andExpect(jsonPath("$.paths./api/v1/waitlist.post").exists())
        // planes de tratamiento.
        .andExpect(jsonPath("$.paths./api/v1/treatment-plans.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/treatment-plans.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/treatment-plans/{id}.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/treatment-plans/{id}.patch").exists())
        .andExpect(jsonPath("$.paths./api/v1/treatment-plans/{id}/status.patch").exists())
        // facturación y pagos.
        .andExpect(jsonPath("$.paths./api/v1/invoices.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/invoices/{id}/payments.post").exists())
        // planes de pago y cuotas (cartera, FASE6-02, FASE6-04).
        .andExpect(jsonPath("$.paths./api/v1/treatment-plans/{id}/payment-plan.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/installments/{id}/pay.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/portfolio/summary.get").exists())
        // CRM leads.
        .andExpect(jsonPath("$.paths./api/v1/leads.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/leads.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/leads/{id}.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/leads/{id}.put").exists())
        .andExpect(jsonPath("$.paths./api/v1/leads/{id}/status.patch").exists())
        .andExpect(jsonPath("$.paths./api/v1/leads/{id}/activities.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/leads/{id}/activities.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/leads/{id}/convert.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/leads/metrics/conversion.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/leads/metrics/response-time.get").exists())
        // especialistas: liquidación de honorarios (FASE7-02).
        .andExpect(jsonPath("$.paths./api/v1/specialists/{id}/settlements.post").exists())
        // inventario: CRUD, movimientos y críticos (FASE7-04).
        .andExpect(jsonPath("$.paths./api/v1/inventory/items.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/inventory/items.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/inventory/critical.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/inventory/items/{id}.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/inventory/items/{id}.patch").exists())
        .andExpect(jsonPath("$.paths./api/v1/inventory/items/{id}.delete").exists())
        .andExpect(jsonPath("$.paths./api/v1/inventory/items/{id}/movements.post").exists())
        // tareas (FASE8-01).
        .andExpect(jsonPath("$.paths./api/v1/tasks.post").exists())
        .andExpect(jsonPath("$.paths./api/v1/tasks.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/tasks/mine.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/tasks/{id}.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/tasks/{id}.patch").exists())
        .andExpect(jsonPath("$.paths./api/v1/tasks/{id}.delete").exists())
        .andExpect(jsonPath("$.paths./api/v1/tasks/{id}/complete.post").exists())
        // oportunidades, acciones y valor recuperado (FASE9-01, FASE9-03, FASE9-04).
        .andExpect(jsonPath("$.paths./api/v1/opportunities.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/opportunities/recovered-value.get").exists())
        .andExpect(jsonPath("$.paths./api/v1/opportunities/{id}/status.patch").exists())
        .andExpect(jsonPath("$.paths./api/v1/opportunities/{id}/actions/{actionId}/execute.post").exists());
  }
}
