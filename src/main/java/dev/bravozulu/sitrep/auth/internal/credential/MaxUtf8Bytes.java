package dev.bravozulu.sitrep.auth.internal.credential;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @Size} counts chars, but BCrypt's input limit is 72 bytes; multi-byte text hits it early.
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = MaxUtf8BytesValidator.class)
public @interface MaxUtf8Bytes {
  int value();

  String message() default "must be at most {value} bytes when UTF-8 encoded";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
