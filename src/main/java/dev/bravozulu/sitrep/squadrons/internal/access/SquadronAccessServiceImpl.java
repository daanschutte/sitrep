package dev.bravozulu.sitrep.squadrons.internal.access;

import dev.bravozulu.sitrep.squadrons.api.SquadronAccessDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronAccessService;
import dev.bravozulu.sitrep.squadrons.api.SquadronGuestAssignmentDto;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignment;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentService;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SquadronAccessServiceImpl implements SquadronAccessService {

  private final SquadronAssignmentService squadronAssignmentService;
  private final SquadronGuestAssignmentService squadronGuestAssignmentService;

  public SquadronAccessServiceImpl(
      SquadronAssignmentService squadronAssignmentService,
      SquadronGuestAssignmentService squadronGuestAssignmentService) {
    this.squadronAssignmentService = squadronAssignmentService;
    this.squadronGuestAssignmentService = squadronGuestAssignmentService;
  }

  @Override
  public SquadronAccessDto getSquadronAccessByUserId(UUID userId) {
    SquadronAssignment squadronAssignment =
        squadronAssignmentService.getSquadronAssignmentByUserId(userId);
    List<SquadronGuestAssignmentDto> guestAssignments =
        squadronGuestAssignmentService.getSquadronGuestAssignment(userId);

    return new SquadronAccessDto(
        userId, squadronAssignment.getSquadronId(), squadronAssignment.getRole(), guestAssignments);
  }

  @Override
  public List<SquadronAccessDto> getSquadronAccessBySquadronIdDtos(UUID squadronId) {
    return List.of(); // TODO
  }
}
