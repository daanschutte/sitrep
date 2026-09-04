package dev.bravozulu.sitrep.squadrons.api;

import java.util.List;
import java.util.UUID;
import org.springframework.modulith.NamedInterface;

@NamedInterface
public interface SquadronAccessService {
  UserSquadronAccessDto getSquadronAccessByUserId(UUID userId);

  List<SquadronAccessDto> getSquadronAccessBySquadronId(UUID squadronId);
}
