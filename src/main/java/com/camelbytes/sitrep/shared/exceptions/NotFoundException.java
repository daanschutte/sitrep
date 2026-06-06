package com.camelbytes.sitrep.shared.exceptions;

public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }

  public NotFoundException(String entity, String id) {
    super(entity + " with id='" + id + "' not found.");
  }
}
