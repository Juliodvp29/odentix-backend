package com.julio.odentix.odentix_backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.julio.odentix.odentix_backend.AbstractIntegrationTest;
import com.julio.odentix.odentix_backend.appointment.entity.Room;
import com.julio.odentix.odentix_backend.appointment.repository.RoomRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import com.julio.odentix.odentix_backend.tenant.entity.Tenant;
import com.julio.odentix.odentix_backend.tenant.repository.TenantRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Pruebas de integración para Room (FASE3-01) contra PostgreSQL
 * real vía Testcontainers.
 *
 * <p>Cubre:
 * <ul>
 *   <li>Persistir y recuperar consultorios (salas).</li>
 *   <li>Búsqueda insensible a mayúsculas/minúsculas con {@code findByNameIgnoreCase}.</li>
 *   <li>Restricción de unicidad de nombre por tenant (mismo tenant falla, distintos tenants coexisten).</li>
 *   <li>Aislamiento cross-tenant estricto (regla de aislamiento multi-tenant del proyecto).</li>
 * </ul>
 */
class RoomRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired
  private RoomRepository roomRepository;

  @Autowired
  private TenantRepository tenantRepository;

  private Tenant tenantA;
  private Tenant tenantB;

  @BeforeEach
  void setUp() {
    TenantContext.clear();

    tenantA = tenantRepository.save(new Tenant("Clínica Alpha Dental", "903111222-1"));
    tenantB = tenantRepository.save(new Tenant("Clínica Gamma Dental", "904333444-2"));
  }

  private Room guardarComo(UUID tenantId, Room room) {
    TenantContext.setTenantId(tenantId);
    try {
      return roomRepository.save(room);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void persistirYRecuperarRoomExitoso() {
    Room consultorio1 = new Room(tenantA.getId(), "Consultorio 1");
    Room guardado = guardarComo(tenantA.getId(), consultorio1);

    assertThat(guardado.getId()).isNotNull();
    assertThat(guardado.getName()).isEqualTo("Consultorio 1");
    assertThat(guardado.isActive()).isTrue();
    assertThat(guardado.getCreatedAt()).isNotNull();
    assertThat(guardado.getUpdatedAt()).isNotNull();

    TenantContext.setTenantId(tenantA.getId());
    try {
      Optional<Room> porNombre = roomRepository.findByNameIgnoreCase("consultorio 1");
      assertThat(porNombre).isPresent();
      assertThat(porNombre.get().getId()).isEqualTo(guardado.getId());
      assertThat(roomRepository.existsByNameIgnoreCase("CONSULTORIO 1")).isTrue();
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void nombreDuplicadoEnMismoTenantFallaPorConstraint() {
    Room consultorio1 = new Room(tenantA.getId(), "Box Dental A");
    guardarComo(tenantA.getId(), consultorio1);

    Room duplicado = new Room(tenantA.getId(), "Box Dental A");

    TenantContext.setTenantId(tenantA.getId());
    try {
      assertThatThrownBy(() -> roomRepository.saveAndFlush(duplicado))
          .isInstanceOf(DataIntegrityViolationException.class);
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void mismoNombreEnDistintosTenantsCoexisteSinConflicto() {
    Room roomA = new Room(tenantA.getId(), "Consultorio Principal");
    Room guardadoA = guardarComo(tenantA.getId(), roomA);

    Room roomB = new Room(tenantB.getId(), "Consultorio Principal");
    Room guardadoB = guardarComo(tenantB.getId(), roomB);

    assertThat(guardadoA.getId()).isNotNull();
    assertThat(guardadoB.getId()).isNotNull();
    assertThat(guardadoA.getId()).isNotEqualTo(guardadoB.getId());

    TenantContext.setTenantId(tenantA.getId());
    try {
      Optional<Room> recuperadoA = roomRepository.findByNameIgnoreCase("Consultorio Principal");
      assertThat(recuperadoA).isPresent();
      assertThat(recuperadoA.get().getId()).isEqualTo(guardadoA.getId());
    } finally {
      TenantContext.clear();
    }
  }

  @Test
  void aislamientoCrossTenantImpideAccesoAConsultoriosAjenos() {
    Room roomA = new Room(tenantA.getId(), "Consultorio A");
    guardadoA(roomA);

    Room roomB = new Room(tenantB.getId(), "Consultorio B");
    guardadoB(roomB);

    TenantContext.setTenantId(tenantA.getId());
    try {
      List<Room> todas = roomRepository.findAll();
      assertThat(todas).extracting(Room::getName).containsExactly("Consultorio A");

      assertThat(roomRepository.findById(roomB.getId())).isEmpty();
      assertThat(roomRepository.findByNameIgnoreCase("Consultorio B")).isEmpty();
    } finally {
      TenantContext.clear();
    }
  }

  private void guardadoA(Room room) {
    guardarComo(tenantA.getId(), room);
  }

  private void guardadoB(Room room) {
    guardarComo(tenantB.getId(), room);
  }
}

