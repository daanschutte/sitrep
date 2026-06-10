package dev.bravozulu.sitrep.users.internal;

import dev.bravozulu.sitrep.shared.exceptions.NotFoundException;
import java.util.UUID;

public class UserNotFoundException extends NotFoundException {
  public UserNotFoundException(UUID id) {
    super("User", id);
  }
}
