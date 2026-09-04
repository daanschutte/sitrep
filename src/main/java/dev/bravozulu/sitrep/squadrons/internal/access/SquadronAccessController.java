package dev.bravozulu.sitrep.squadrons.internal.access;

import dev.bravozulu.sitrep.squadrons.api.SquadronAccessDto;
import dev.bravozulu.sitrep.squadrons.api.UserSquadronAccessDto;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/squadrons")
@Validated
public class SquadronAccessController {
  private final SquadronAccessCoordinator coordinator;

  public SquadronAccessController(SquadronAccessCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  @GetMapping("/access")
  public UserSquadronAccessDto getByUserId(@RequestParam UUID userId) {
    return coordinator.getSquadronAccessByUserId(userId);
  }

  @GetMapping("/{squadronId}/access")
  public List<SquadronAccessDto> getBySquadronId(@PathVariable UUID squadronId) {
    return coordinator.getSquadronAccessBySquadronId(squadronId);
  }
}
