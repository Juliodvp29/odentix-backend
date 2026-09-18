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
        .andExpect(jsonPath("$.paths./api/v1/invoices/{id}/payments.post").exists());
  }
}
