package dev.bravozulu.sitrep.squadrons.internal.guestaccess;

import dev.bravozulu.sitrep.shared.exceptions.NotFoundException;

public class SquadronGuestAssignmentNotFoundException extends NotFoundException {
  public SquadronGuestAssignmentNotFoundException(String message) {
    super(message);
  }
}
