package dev.bravozulu.sitrep.squadrons.internal.access;

import dev.bravozulu.sitrep.squadrons.api.SquadronAccessDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronAccessService;
import dev.bravozulu.sitrep.squadrons.api.SquadronAssignmentDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronGuestAssignmentDto;
import dev.bravozulu.sitrep.squadrons.api.UserSquadronAccessDto;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentService;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentService;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
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
  public UserSquadronAccessDto getSquadronAccessByUserId(UUID userId) {
    SquadronAssignmentDto squadronAssignment =
        squadronAssignmentService.getSquadronAssignmentByUserId(userId);
    List<SquadronGuestAssignmentDto> guestAssignments =
        squadronGuestAssignmentService.getSquadronGuestAssignmentsByUserId(userId);

    return new UserSquadronAccessDto(
        squadronAssignment.squadronId(), userId, squadronAssignment.role(), guestAssignments);
  }

  @Override
  public List<SquadronAccessDto> getSquadronAccessBySquadronId(UUID squadronId) {
    Stream<SquadronAccessDto> squadronAssignments =
        squadronAssignmentService.getSquadronAssignmentsBySquadronId(squadronId).stream()
            .map(this::toSquadronAccessDto);
    Stream<SquadronAccessDto> guestAssignments =
        squadronGuestAssignmentService.getSquadronGuestAssignmentsBySquadronId(squadronId).stream()
            .map(this::toSquadronAccessDto);

    return Stream.concat(squadronAssignments, guestAssignments).toList();
  }

  private SquadronAccessDto toSquadronAccessDto(SquadronAssignmentDto assignment) {
    return new SquadronAccessDto(assignment.squadronId(), assignment.userId(), assignment.role());
  }

  private SquadronAccessDto toSquadronAccessDto(SquadronGuestAssignmentDto assignment) {
    return new SquadronAccessDto(
        assignment.squadronId(), assignment.userId(), assignment.role(), true);
  }
}
