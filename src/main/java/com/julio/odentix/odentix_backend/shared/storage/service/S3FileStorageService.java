package com.julio.odentix.odentix_backend.shared.storage.service;

import com.julio.odentix.odentix_backend.shared.storage.FileStorageService;
import com.julio.odentix.odentix_backend.shared.storage.config.S3StorageProperties;
import com.julio.odentix.odentix_backend.shared.storage.exception.StorageException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Implementación de almacenamiento sobre AWS S3 o proveedores compatibles (FASE2-09).
 */
@Service
public class S3FileStorageService implements FileStorageService {

  private static final Logger log = LoggerFactory.getLogger(S3FileStorageService.class);

  private final S3Client s3Client;
  private final S3StorageProperties properties;

  public S3FileStorageService(S3Client s3Client, S3StorageProperties properties) {
    this.s3Client = s3Client;
    this.properties = properties;
  }

  @Override
  public String upload(String key, InputStream inputStream, long contentLength, String contentType) {
    try {
      PutObjectRequest.Builder requestBuilder = PutObjectRequest.builder()
          .bucket(properties.getBucket())
          .key(key)
          .contentLength(contentLength);

      if (contentType != null && !contentType.isBlank()) {
        requestBuilder.contentType(contentType);
      }

      s3Client.putObject(requestBuilder.build(), RequestBody.fromInputStream(inputStream, contentLength));
      log.info("Archivo subido a S3: bucket={}, key={}", properties.getBucket(), key);
      return key;
    } catch (SdkException e) {
      log.error("Error al subir archivo a S3 con key={}: {}", key, e.getMessage());
      throw new StorageException("Error al subir archivo a almacenamiento: " + e.getMessage(), e);
    }
  }

  @Override
  public void delete(String key) {
    try {
      DeleteObjectRequest request = DeleteObjectRequest.builder()
          .bucket(properties.getBucket())
          .key(key)
          .build();
      s3Client.deleteObject(request);
      log.info("Archivo eliminado de S3: bucket={}, key={}", properties.getBucket(), key);
    } catch (SdkException e) {
      log.warn("Fallo al eliminar archivo de S3 con key={}: {}", key, e.getMessage());
      // Silencioso para permitir compensaciones en caso de error
    }
  }
}
