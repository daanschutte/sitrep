package com.camelbytes.sitrep.squadrons.internal.assignment;

import com.camelbytes.sitrep.shared.exceptions.NotFoundException;

import java.util.UUID;

public class SquadronAssignmentNotFoundException extends NotFoundException {
  public SquadronAssignmentNotFoundException(UUID userId) {
    super("Squadron assignment for userId=" + userId.toString() + " not found");
  }
}
