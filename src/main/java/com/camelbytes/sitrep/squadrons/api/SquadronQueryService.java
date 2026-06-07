package com.camelbytes.sitrep.squadrons.api;

import org.springframework.modulith.NamedInterface;

import java.util.UUID;

@NamedInterface
public interface SquadronQueryService {
  void validateSquadronExists(UUID squadronId);
}
