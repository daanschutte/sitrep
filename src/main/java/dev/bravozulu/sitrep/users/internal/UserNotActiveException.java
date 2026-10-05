package dev.bravozulu.sitrep.users.internal;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import java.util.UUID;

public class UserNotActiveException extends ConflictException {
  public UserNotActiveException(UUID userId) {
    super("User with userId=" + userId + " is not active");
  }
}
