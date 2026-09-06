package com.julio.odentix.odentix_backend.auth.entity;

/**
 * Roles de usuario dentro de una clínica (FASE1-03).
 *
 * <p>Los valores coinciden con el tipo enumerado PostgreSQL `user_role` (docs/schema.sql §2):
 * 'propietario', 'odontologo', 'recepcion', 'auxiliar', 'especialista_externo'.
 */
public enum UserRole {
  propietario,
  odontologo,
  recepcion,
  auxiliar,
  especialista_externo
}
