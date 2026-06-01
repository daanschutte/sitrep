package com.camelbytes.sitrep.users.internal;

import com.camelbytes.sitrep.shared.exceptions.NotFoundException;

public class UserNotFoundException extends NotFoundException {
  public UserNotFoundException(String id) {
    super("User with id='" + id + "' not found");
  }
}
