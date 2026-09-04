package dev.bravozulu.sitrep.squadrons.api;

import java.util.UUID;

public record SquadronAccessDto(UUID squadronId, UUID userId, SquadronRole role, boolean isGuest) {
  public SquadronAccessDto(UUID squadronId, UUID userId, SquadronRole role) {
    this(squadronId, userId, role, false);
  }
}
