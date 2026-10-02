package dev.bravozulu.sitrep.users.internal;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;

public class UserNotActiveException extends ConflictException {
  public UserNotActiveException(String message) {
    super(message);
  }
}
