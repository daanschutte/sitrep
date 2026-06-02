package com.camelbytes.sitrep.users.internal;

import com.camelbytes.sitrep.shared.exceptions.NotFoundException;

import java.util.UUID;

public class UserNotFoundException extends NotFoundException {
  public UserNotFoundException(UUID id) {
    super("User", id.toString());
  }
}
