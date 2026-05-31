package com.camelbytes.sitrep.shared.exceptions;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Void> handleConstraintViolation() {
    return ResponseEntity.unprocessableContent().build();
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Void> handleMethodArgumentViolation() {
    return ResponseEntity.unprocessableContent().build();
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<Void> handleNotFound() {
    return ResponseEntity.notFound().build();
  }
}
