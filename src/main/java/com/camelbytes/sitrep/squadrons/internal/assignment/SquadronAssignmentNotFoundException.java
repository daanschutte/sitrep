package com.camelbytes.sitrep.squadrons.internal.assignment;

import com.camelbytes.sitrep.shared.exceptions.NotFoundException;

public class SquadronAssignmentNotFoundException extends NotFoundException {
  public SquadronAssignmentNotFoundException(String message) {
    super(message);
  }
}
