package com.julio.odentix.odentix_backend.shared.controller;

import com.julio.odentix.odentix_backend.shared.entity.TestBusinessEntity;
import com.julio.odentix.odentix_backend.shared.repository.TestBusinessEntityRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controlador REST de prueba para validar el aislamiento cross-tenant (FASE1-10).
 *
 * <p>Expone operaciones de lectura y modificación sobre {@link TestBusinessEntity}.
 * Vive únicamente en {@code src/test/} para no desplegarse en producción.
 *
 * <p>El controlador delega directamente en {@link TestBusinessEntityRepository},
 * confiando en que el filtro de Hibernate (vía {@code @TenantId} y {@code TenantIdentifierResolver})
 * asegure que ninguna petición pueda acceder a datos de otro tenant.
 */
@RestController
@RequestMapping("/api/v1/test-entities")
public class TestBusinessEntityController {

  private final TestBusinessEntityRepository repository;

  public TestBusinessEntityController(TestBusinessEntityRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/{id}")
  public ResponseEntity<TestEntityResponse> getById(@PathVariable("id") UUID id) {
    TestBusinessEntity entity = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entidad no encontrada"));
    return ResponseEntity.ok(new TestEntityResponse(entity.getId(), entity.getName(), entity.getTenantId()));
  }

  @PutMapping("/{id}")
  public ResponseEntity<TestEntityResponse> update(
      @PathVariable("id") UUID id,
      @RequestBody UpdateTestEntityRequest request) {
    TestBusinessEntity entity = repository.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entidad no encontrada"));

    entity.setName(request.name());
    TestBusinessEntity updated = repository.save(entity);

    return ResponseEntity.ok(new TestEntityResponse(updated.getId(), updated.getName(), updated.getTenantId()));
  }

  @GetMapping
  public ResponseEntity<List<TestEntityResponse>> listAll() {
    List<TestEntityResponse> list = repository.findAll().stream()
        .map(e -> new TestEntityResponse(e.getId(), e.getName(), e.getTenantId()))
        .toList();
    return ResponseEntity.ok(list);
  }

  public record UpdateTestEntityRequest(String name) {}

  public record TestEntityResponse(UUID id, String name, UUID tenantId) {}
}
