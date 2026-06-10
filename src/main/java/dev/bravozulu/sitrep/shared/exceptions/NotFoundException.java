package dev.bravozulu.sitrep.shared.exceptions;

import java.util.UUID;

public class NotFoundException extends RuntimeException {
  public NotFoundException(String message) {
    super(message);
  }

  public NotFoundException(String entity, UUID id) {
    super(entity + " with id='" + id + "' not found.");
  }
}
