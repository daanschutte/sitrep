package dev.bravozulu.sitrep.squadrons.internal.squadron;

import dev.bravozulu.sitrep.shared.exceptions.NotFoundException;
import java.util.UUID;

public class SquadronNotFoundException extends NotFoundException {
  public SquadronNotFoundException(UUID id) {
    super("Squadron", id);
  }
}
