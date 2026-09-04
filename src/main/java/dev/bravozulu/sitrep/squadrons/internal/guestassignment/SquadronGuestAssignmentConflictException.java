package dev.bravozulu.sitrep.squadrons.internal.guestassignment;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;

public class SquadronGuestAssignmentConflictException extends ConflictException {
  public SquadronGuestAssignmentConflictException(String message) {
    super(message);
  }
}
