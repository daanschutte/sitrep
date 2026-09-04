package dev.bravozulu.sitrep.squadrons.internal.assignment;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import dev.bravozulu.sitrep.squadrons.api.SquadronAssignmentDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronQueryService;
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
    return repository.findBySquadronIdAndRevokedAtIsNull(squadronId).stream()
        .map(this::toDto)
        .toList();
  }

  public SquadronAssignmentDto getSquadronAssignmentByUserId(UUID userId) {
    return repository
        .findByUserIdAndRevokedAtIsNull(userId)
        .map(this::toDto)
        .orElseThrow(
            () ->
                new SquadronAssignmentNotFoundException(
                    "Squadron assignment for userId=" + userId.toString() + " not found"));
  }

  @Transactional
  public void createSquadronAssignment(UUID squadronId, SquadronAssignmentCreateRequest request) {
    squadronQueryService.validateSquadronExists(squadronId);
    userQueryService.validateUserExists(request.userId());

    repository
        .findByUserIdAndRevokedAtIsNull(request.userId())
        .ifPresentOrElse(
            existing -> {
              if (existing.getSquadronId().equals(squadronId)) {
                if (existing.getRole().equals(request.role())) {
                  log.error(
                      "userId={} already holds role={} at squadronId={}",
                      request.userId(),
                      request.role(),
                      squadronId);
                } else {
                  existing.setRole(request.role());
                  repository.saveAndFlush(existing);
                }
              }
            },
            () -> {
              log.debug("No existing squadron assignments for userId={}", request.userId());

              SquadronAssignment assignment =
                  new SquadronAssignment(squadronId, request.userId(), request.role());

              try {
                assignment = repository.save(assignment);
                log.debug(
                    "Squadron assignment with id={} created ({}:{}) in role={}",
                    assignment.getId(),
                    assignment.getSquadronId(),
                    assignment.getUserId(),
                    assignment.getRole());
              } catch (DataIntegrityViolationException exception) {
                String message =
                    String.format(
                        "Could not assign userID='%s' to squadron='%s'",
                        request.userId(), squadronId);
                throw new ConflictException(message);
              }
            });
  }

  @Transactional
  public void revokeSquadronAssignment(UUID squadronId, UUID userId) {
    SquadronAssignment assignment =
        repository
            .findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId)
            .orElseThrow(
                () ->
                    new SquadronAssignmentNotFoundException(
                        "Squadron assignment for squadronId="
                            + squadronId.toString()
                            + " userId="
                            + userId.toString()
                            + " not found"));

    assignment.revokeAssignment(Instant.now());
  }

  private SquadronAssignmentDto toDto(SquadronAssignment assignment) {
    return new SquadronAssignmentDto(
        assignment.getSquadronId(), assignment.getUserId(), assignment.getRole());
  }
}
