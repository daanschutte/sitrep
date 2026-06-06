package com.camelbytes.sitrep.squadrons.internal.assignment;

import com.camelbytes.sitrep.shared.exceptions.NotFoundException;
import java.util.UUID;

public class SquadronAssignmentNotFoundException extends NotFoundException {
  public SquadronAssignmentNotFoundException(UUID userId) {
    super("SquadronAssignment", userId);
  }

  public SquadronAssignmentNotFoundException(String message) {
    super(message);
  }
}
