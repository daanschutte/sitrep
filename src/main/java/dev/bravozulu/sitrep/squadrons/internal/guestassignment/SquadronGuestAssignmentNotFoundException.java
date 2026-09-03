package dev.bravozulu.sitrep.squadrons.internal.guestassignment;

import dev.bravozulu.sitrep.shared.exceptions.NotFoundException;

public class SquadronGuestAssignmentNotFoundException extends NotFoundException {
  public SquadronGuestAssignmentNotFoundException(String message) {
    super(message);
  }
}
