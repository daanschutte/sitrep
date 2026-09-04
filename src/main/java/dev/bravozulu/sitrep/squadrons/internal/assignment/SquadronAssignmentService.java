package dev.bravozulu.sitrep.squadrons.internal.assignment;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import dev.bravozulu.sitrep.shared.exceptions.NotFoundException;
import dev.bravozulu.sitrep.squadrons.api.SquadronAssignmentDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronQueryService;
import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
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
public class SquadronAssignmentService {
  private static final Logger log = LoggerFactory.getLogger(SquadronAssignmentService.class);

  private final SquadronAssignmentRepository repository;
  private final SquadronQueryService squadronQueryService;
  private final UserQueryService userQueryService;

  public SquadronAssignmentService(
      SquadronAssignmentRepository repository,
      SquadronQueryService squadronQueryService,
      UserQueryService userQueryService) {
    this.repository = repository;
    this.squadronQueryService = squadronQueryService;
    this.userQueryService = userQueryService;
  }

  public boolean isUserPrimarySquadron(UUID squadronId, UUID userId) {
    return repository
        .findByUserIdAndRevokedAtIsNull(userId)
        .map(SquadronAssignment::getSquadronId)
        .filter(userSquadron -> userSquadron.equals(squadronId))
        .isPresent();
  }

  public List<SquadronAssignmentDto> getSquadronAssignmentsBySquadronId(UUID squadronId) {
    return repository.findAllBySquadronIdAndRevokedAtIsNull(squadronId);
  }

  public SquadronAssignmentDto getSquadronAssignmentByUserId(UUID userId) {
    return repository
        .findByUserIdAndRevokedAtIsNull(userId)
        .map(this::toDto)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Squadron assignment for userId=" + userId.toString() + " not found"));
  }

  @Transactional
  public void assignSquadron(UUID squadronId, SquadronAssignmentCreateRequest request) {
    squadronQueryService.validateSquadronExists(squadronId);
    userQueryService.validateUserExists(request.userId());

    if (repository.findByUserIdAndRevokedAtIsNull(request.userId()).isPresent()) {
      throw new ConflictException(
          "userId=" + request.userId() + " already has an active squadron assignment");
    }

    create(squadronId, request);
  }

  @Transactional
  public void transferSquadronAssignment(
      UUID newSquadronId, SquadronAssignmentCreateRequest request) {
    squadronQueryService.validateSquadronExists(newSquadronId);
    userQueryService.validateUserExists(request.userId());

    SquadronAssignment existing =
        repository
            .findByUserIdAndRevokedAtIsNull(request.userId())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "No active squadron assignment for userId="
                            + request.userId()
                            + " to transfer"));

    if (existing.getSquadronId().equals(newSquadronId)) {
      throw new ConflictException(
          "userId=" + request.userId() + " is already assigned to squadronId=" + newSquadronId);
    }

    revoke(existing);
    create(newSquadronId, request);
  }

  @Transactional
  public void changeSquadronRole(UUID squadronId, UUID userId, SquadronRole role) {
    SquadronAssignment assignment =
        repository
            .findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Squadron assignment for squadronId="
                            + squadronId.toString()
                            + " userId="
                            + userId.toString()
                            + " not found"));

    changeRole(assignment, role);
  }

  @Transactional
  public void revokeSquadronAssignment(UUID squadronId, UUID userId) {
    SquadronAssignment assignment =
        repository
            .findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Squadron assignment for squadronId="
                            + squadronId.toString()
                            + " userId="
                            + userId.toString()
                            + " not found"));

    revoke(assignment);
  }

  private void create(UUID squadronId, SquadronAssignmentCreateRequest request) {
    SquadronAssignment assignment =
        new SquadronAssignment(squadronId, request.userId(), request.role());
    try {
      assignment = repository.saveAndFlush(assignment);
      log.debug(
          "Squadron assignment with id={} created ({}:{}) in role={}",
          assignment.getId(),
          assignment.getSquadronId(),
          assignment.getUserId(),
          assignment.getRole());
    } catch (DataIntegrityViolationException exception) {
      throw new ConflictException(
          String.format(
              "Could not assign userID='%s' to squadron='%s'", request.userId(), squadronId));
    }
  }

  private void revoke(SquadronAssignment assignment) {
    assignment.revoke(Instant.now());
    repository.saveAndFlush(assignment);
    log.debug(
        "Squadron assignment with id={} ended for user={}",
        assignment.getId(),
        assignment.getUserId());
  }

  private void changeRole(SquadronAssignment assignment, SquadronRole role) {
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
    log.debug("Squadron assignment with id={} role changed to={}", assignment.getId(), role);
  }

  private SquadronAssignmentDto toDto(SquadronAssignment assignment) {
    return new SquadronAssignmentDto(
        assignment.getSquadronId(), assignment.getUserId(), assignment.getRole());
  }
}
