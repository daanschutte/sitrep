package com.camelbytes.sitrep.squadrons.internal.squadron;

import com.camelbytes.sitrep.shared.exceptions.NotFoundException;
import java.util.UUID;

public class SquadronNotFoundException extends NotFoundException {
  public SquadronNotFoundException(UUID id) {
    super("Squadron", id.toString());
  }
}
