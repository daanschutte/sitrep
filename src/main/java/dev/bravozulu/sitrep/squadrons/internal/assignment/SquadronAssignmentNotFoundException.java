package dev.bravozulu.sitrep.squadrons.internal.assignment;

import dev.bravozulu.sitrep.shared.exceptions.NotFoundException;

public class SquadronAssignmentNotFoundException extends NotFoundException {
  public SquadronAssignmentNotFoundException(String message) {
    super(message);
  }
}
