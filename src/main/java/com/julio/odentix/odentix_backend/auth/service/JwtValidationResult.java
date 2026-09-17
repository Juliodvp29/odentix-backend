package com.julio.odentix.odentix_backend.auth.service;

/**
 * Resultado de la validación estructural y criptográfica de un JWT.
 * Permite distinguir entre tokens vigentes, expirados o manipulados.
 */
public enum JwtValidationResult {
  VALID,
  EXPIRED,
  INVALID
}
