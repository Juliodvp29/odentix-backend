package com.julio.odentix.odentix_backend.shared.storage.config;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

/**
 * Configuración del cliente S3 SDK v2 (FASE2-09).
 */
@Configuration
public class S3StorageConfig {

  @Bean
  @ConditionalOnMissingBean
  public S3Client s3Client(S3StorageProperties properties) {
    S3ClientBuilder builder = S3Client.builder()
        .region(Region.of(properties.getRegion()));

    if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
      builder.endpointOverride(URI.create(properties.getEndpoint()));
    }

    if (properties.getAccessKey() != null && !properties.getAccessKey().isBlank()
        && properties.getSecretKey() != null && !properties.getSecretKey().isBlank()) {
      builder.credentialsProvider(StaticCredentialsProvider.create(
          AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())));
    } else {
      builder.credentialsProvider(DefaultCredentialsProvider.create());
    }

    if (properties.isPathStyleAccessEnabled()) {
      builder.serviceConfiguration(S3Configuration.builder()
          .pathStyleAccessEnabled(true)
          .build());
    }

    return builder.build();
  }
}
