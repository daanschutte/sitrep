package com.camelbytes.sitrep.squadrons.api;

import java.util.UUID;
import org.springframework.modulith.NamedInterface;

@NamedInterface
public interface SquadronQueryService {
  void validateSquadronExists(UUID squadronId);
}
