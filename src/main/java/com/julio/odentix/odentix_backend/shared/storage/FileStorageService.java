package com.julio.odentix.odentix_backend.shared.storage;

import java.io.InputStream;

/**
 * Contrato de servicio de almacenamiento de archivos (FASE2-09).
 * Desacopla la lógica de negocio del proveedor de almacenamiento subyacente (S3, MinIO, R2, etc.).
 */
public interface FileStorageService {

  /**
   * Sube un archivo al almacenamiento.
   *
   * @param key clave única del objeto (ej. tenants/{tenantId}/patients/{patientId}/{uuid}-{fileName})
   * @param inputStream flujo de bytes del archivo
   * @param contentLength tamaño en bytes del archivo
   * @param contentType tipo MIME del archivo (opcional/anulable)
   * @return la clave de almacenamiento persistida
   */
  String upload(String key, InputStream inputStream, long contentLength, String contentType);

  /**
   * Elimina un archivo del almacenamiento. Si el archivo no existe, no debe fallar.
   *
   * @param key clave única del objeto
   */
  void delete(String key);
}
