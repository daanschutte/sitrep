package dev.bravozulu.sitrep.squadrons.internal.guestassignment;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import dev.bravozulu.sitrep.squadrons.api.SquadronGuestAssignmentDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronQueryService;
import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentService;
import dev.bravozulu.sitrep.users.api.UserQueryService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SquadronGuestAssignmentService {
  private static final Logger log = LoggerFactory.getLogger(SquadronGuestAssignmentService.class);

  private final SquadronAssignmentService squadronAssignmentService;
  private final SquadronGuestAssignmentRepository repository;
  private final SquadronQueryService squadronQueryService;
  private final UserQueryService userQueryService;

  public SquadronGuestAssignmentService(
      SquadronGuestAssignmentRepository repository,
      SquadronAssignmentService squadronAssignmentService,
      SquadronQueryService squadronQueryService,
      UserQueryService userQueryService) {
    this.repository = repository;
    this.squadronAssignmentService = squadronAssignmentService;
    this.squadronQueryService = squadronQueryService;
    this.userQueryService = userQueryService;
  }

  public List<SquadronGuestAssignmentDto> getSquadronGuestAssignmentsByUserId(UUID userId) {
    return repository.findAllByUserIdAndRevokedAtIsNull(userId).stream().map(this::toDto).toList();
  }

  public List<SquadronGuestAssignmentDto> getSquadronGuestAssignmentsBySquadronId(UUID squadronId) {
    return repository.findBySquadronIdAndRevokedAtIsNull(squadronId);
  }

  @Transactional
  public void assignGuestSquadron(UUID squadronId, SquadronGuestAssignmentCreateRequest request) {
    squadronQueryService.validateSquadronExists(squadronId);
    userQueryService.validateUserExists(request.userId());

    if (squadronAssignmentService.isUserPrimarySquadron(squadronId, request.userId())) {
      log.error(
          "Could not assign guest access: squadronId={} already primary squadron of userId={}",
          squadronId,
          request.userId().toString());

      throw new SquadronGuestAssignmentConflictException(
          "Cannot assign userId="
              + request.userId()
              + " as guest in their primary squadronId="
              + squadronId.toString());
    }

    if (repository
        .findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, request.userId())
        .isPresent()) {
      throw new ConflictException(
          "userId="
              + request.userId()
              + " already has active guest access to squadronId="
              + squadronId);
    }

    create(squadronId, request);
  }

  @Transactional
  public void changeGuestSquadronRole(UUID squadronId, UUID userId, SquadronRole role) {
    SquadronGuestAssignment assignment =
        repository
            .findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId)
            .orElseThrow(
                () ->
                    new SquadronGuestAssignmentNotFoundException(
                        "Squadron guest assignment for squadronId="
                            + squadronId.toString()
                            + " userId="
                            + userId.toString()
                            + " not found"));

    changeRole(assignment, role);
  }

  @Transactional
  public void revokeSquadronGuestAssignment(UUID squadronId, UUID userId) {
    SquadronGuestAssignment guestAssignment =
        repository
            .findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId)
            .orElseThrow(
                () ->
                    new SquadronGuestAssignmentNotFoundException(
                        "Squadron assignment for squadronId="
                            + squadronId.toString()
                            + " userId="
                            + userId.toString()
                            + " not found"));

    guestAssignment.revokeAccess(Instant.now());
    repository.saveAndFlush(guestAssignment);
  }

  private void create(UUID squadronId, SquadronGuestAssignmentCreateRequest request) {
    SquadronGuestAssignment access =
        new SquadronGuestAssignment(squadronId, request.userId(), request.role());
    try {
      access = repository.save(access);
      log.debug(
          "Squadron guest access with id={} created ({}:{}) in role={}",
          access.getId(),
          access.getSquadronId(),
          access.getUserId(),
          access.getRole());
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException(
          String.format(
              "Could not guest assign userID='%s' to squadron='%s'", request.userId(), squadronId));
    }
  }

  private void changeRole(SquadronGuestAssignment assignment, SquadronRole role) {
    if (assignment.getRole() == role) {
      log.debug(
          "userId={} already holds role={} at squadronId={}",
          assignment.getUserId(),
          role,
          assignment.getSquadronId());
      return;
    }

    assignment.setRole(role);
    repository.saveAndFlush(assignment);
    log.debug("Squadron guest assignment with id={} role changed to={}", assignment.getId(), role);
  }

  private SquadronGuestAssignmentDto toDto(SquadronGuestAssignment guestAssignment) {
    return new SquadronGuestAssignmentDto(
        guestAssignment.getSquadronId(), guestAssignment.getUserId(), guestAssignment.getRole());
  }
}
