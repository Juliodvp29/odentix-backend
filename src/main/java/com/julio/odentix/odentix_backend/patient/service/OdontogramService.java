package com.julio.odentix.odentix_backend.patient.service;

import com.julio.odentix.odentix_backend.patient.dto.CreateOdontogramEntryRequest;
import com.julio.odentix.odentix_backend.patient.dto.OdontogramEntryResponse;
import com.julio.odentix.odentix_backend.patient.dto.OdontogramResponse;
import com.julio.odentix.odentix_backend.patient.dto.OdontogramResponse.ToothGroup;
import com.julio.odentix.odentix_backend.patient.entity.OdontogramEntry;
import com.julio.odentix.odentix_backend.patient.entity.OdontogramEntryType;
import com.julio.odentix.odentix_backend.patient.entity.Patient;
import com.julio.odentix.odentix_backend.patient.repository.OdontogramEntryRepository;
import com.julio.odentix.odentix_backend.shared.context.TenantContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de odontograma (FASE2-08).
 *
 * <p>Igual que historia clínica: el tenant sale del {@code TenantContext},
 * el paciente se valida activo con {@code PatientService} (inexistente,
 * inactivo o ajeno → 404) y el autor queda {@code null} hasta que existan
 * profesionales en Fase 3. Registrar nunca modifica entradas existentes:
 * cada POST agrega una fila nueva.
 */
@Service
public class OdontogramService {

  private final OdontogramEntryRepository odontogramEntryRepository;
  private final PatientService patientService;

  public OdontogramService(
      OdontogramEntryRepository odontogramEntryRepository, PatientService patientService) {
    this.odontogramEntryRepository = odontogramEntryRepository;
    this.patientService = patientService;
  }

  @Transactional
  public OdontogramEntryResponse addEntry(UUID patientId, CreateOdontogramEntryRequest request) {
    Patient patient = patientService.findActiveEntity(patientId);
    OdontogramEntry entry = new OdontogramEntry(
        TenantContext.getRequiredTenantId(),
        patient,
        request.getToothNumber().shortValue(),
        request.getEntryType(),
        request.getCondition());
    entry.setSurface(request.getSurface());
    entry.setNotes(request.getNotes());
    if (request.getRecordedAt() != null) {
      entry.setRecordedAt(request.getRecordedAt());
    }
    return OdontogramEntryResponse.fromEntity(odontogramEntryRepository.save(entry));
  }

  @Transactional(readOnly = true)
  public OdontogramResponse getOdontogram(UUID patientId) {
    patientService.findActiveEntity(patientId);
    List<OdontogramEntry> entries = odontogramEntryRepository
        .findAllByTenantIdAndPatientId(TenantContext.getRequiredTenantId(), patientId);

    // Piezas ordenadas numéricamente (TreeMap); dentro de cada pieza, los
    // cuatro tipos siempre presentes, cada uno ordenado por fecha desc.
    Map<Short, List<OdontogramEntry>> byTooth = new TreeMap<>();
    for (OdontogramEntry entry : entries) {
      byTooth.computeIfAbsent(entry.getToothNumber(), tooth -> new ArrayList<>()).add(entry);
    }

    List<ToothGroup> teeth = new ArrayList<>();
    for (Map.Entry<Short, List<OdontogramEntry>> group : byTooth.entrySet()) {
      Map<OdontogramEntryType, List<OdontogramEntryResponse>> byType =
          new EnumMap<>(OdontogramEntryType.class);
      for (OdontogramEntryType type : OdontogramEntryType.values()) {
        byType.put(type, new ArrayList<>());
      }
      group.getValue().stream()
          .sorted(Comparator.comparing(OdontogramEntry::getRecordedAt).reversed())
          .map(OdontogramEntryResponse::fromEntity)
          .forEach(response -> byType.get(response.getEntryType()).add(response));
      teeth.add(new ToothGroup(group.getKey(), byType));
    }
    return new OdontogramResponse(patientId, teeth);
  }
}
