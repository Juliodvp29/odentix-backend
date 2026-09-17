package com.julio.odentix.odentix_backend.patient.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Valida que un número de pieza dental use notación FDI de dentición
 * permanente (FASE2-08): 11–18, 21–28, 31–38, 41–48.
 *
 * <p>El CHECK de la migración V8 solo acota 11–48, así que valores como 19
 * pasarían la BD; esta anotación los rechaza en la entrada con un 400.
 * Acepta {@code null} (la obligatoriedad la marca {@code @NotNull} aparte).
 */
@Documented
@Constraint(validatedBy = FdiToothNumberValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface FdiToothNumber {

  String message() default "El número de pieza dental debe usar notación FDI permanente (11–18, 21–28, 31–38, 41–48)";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
