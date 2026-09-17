package com.julio.odentix.odentix_backend.patient.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validador de {@link FdiToothNumber}: acepta solo piezas permanentes FDI.
 */
public class FdiToothNumberValidator implements ConstraintValidator<FdiToothNumber, Integer> {

  @Override
  public boolean isValid(Integer value, ConstraintValidatorContext context) {
    if (value == null) {
      return true;
    }
    int quadrant = value / 10;
    int piece = value % 10;
    return quadrant >= 1 && quadrant <= 4 && piece >= 1 && piece <= 8;
  }
}
