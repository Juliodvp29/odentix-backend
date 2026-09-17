package com.julio.odentix.odentix_backend.shared.storage.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propiedades de configuración para almacenamiento compatible con S3 (FASE2-09).
 */
@Component
@ConfigurationProperties(prefix = "storage.s3")
public class S3StorageProperties {

  private String bucket = "odentix-files";
  private String region = "us-east-1";
  private String endpoint;
  private String accessKey;
  private String secretKey;
  private boolean pathStyleAccessEnabled = false;

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public String getRegion() {
    return region;
  }

  public void setRegion(String region) {
    this.region = region;
  }

  public String getEndpoint() {
    return endpoint;
  }

  public void setEndpoint(String endpoint) {
    this.endpoint = endpoint;
  }

  public String getAccessKey() {
    return accessKey;
  }

  public void setAccessKey(String accessKey) {
    this.accessKey = accessKey;
  }

  public String getSecretKey() {
    return secretKey;
  }

  public void setSecretKey(String secretKey) {
    this.secretKey = secretKey;
  }

  public boolean isPathStyleAccessEnabled() {
    return pathStyleAccessEnabled;
  }

  public void setPathStyleAccessEnabled(boolean pathStyleAccessEnabled) {
    this.pathStyleAccessEnabled = pathStyleAccessEnabled;
  }
}
