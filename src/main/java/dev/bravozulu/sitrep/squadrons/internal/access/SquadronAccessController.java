package dev.bravozulu.sitrep.squadrons.internal.access;

import dev.bravozulu.sitrep.squadrons.api.SquadronAccessDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronAccessService;
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
  private final SquadronAccessService service;

  public SquadronAccessController(SquadronAccessService service) {
    this.service = service;
  }

  @GetMapping("/assignments")
  public SquadronAccessDto getByUserId(@RequestParam UUID userId) {
    return service.getSquadronAccessByUserId(userId);
  }

  @GetMapping("/{squadronId}/assignments")
  public List<SquadronAccessDto> getBySquadronId(@PathVariable UUID squadronId) {
    return service.getSquadronAccessBySquadronIdDtos(squadronId);
  }
}
