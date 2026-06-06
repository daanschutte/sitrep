package com.camelbytes.sitrep.squadrons.internal.assignment;

import com.camelbytes.sitrep.shared.exceptions.ConflictException;
import com.camelbytes.sitrep.squadrons.api.SquadronAssignmentDto;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class SquadronAssignmentService {
  private static final Logger log = LoggerFactory.getLogger(SquadronAssignmentService.class);

  private final SquadronAssignmentRepository repository;

  public SquadronAssignmentService(SquadronAssignmentRepository repository) {
    this.repository = repository;
  }

  public List<SquadronAssignmentDto> getCurrentSquadronAssignmentsBySquadronId(UUID squadronId) {
    return repository.findSquadronAssignmentsBySquadronIdAndIsCurrent(squadronId, true).stream()
        .map(SquadronAssignmentService::toDto)
        .toList();
  }

  public SquadronAssignmentDto getSquadronAssignmentByUserId(UUID userId) {
    // TODO: include cross-squadron assignment

    return repository
        .findSquadronAssignmentByUserId(userId)
        .map(SquadronAssignmentService::toDto)
        .orElseThrow(
            () ->
                new SquadronAssignmentNotFoundException(
                    "Squadron assignment for userId=" + userId.toString() + " not found"));
  }

  @Transactional
  public UUID createSquadronAssignment(SquadronAssignmentCreateRequest request) {
    SquadronAssignment assignment =
        new SquadronAssignment(request.userId(), request.squadronId(), request.role());

    repository
        .findSquadronAssignmentByUserId(request.userId())
        .ifPresent(
            existing -> {
              existing.endAssignment();
              repository.save(existing);
              log.debug(
                  "Existing squadron assignment with id={} ended for user={}",
                  existing.getId(),
                  existing.getUserId());
            });

    try {
      assignment = repository.save(assignment);
      log.debug(
          "Squadron assignment with id={} created ({}:{}) in role={}",
          assignment.getId(),
          assignment.getUserId(),
          assignment.getSquadronId(),
          assignment.getRole());
    } catch (DataIntegrityViolationException exception) {
      String message =
          String.format(
              "Could not create squadron assignment for userID='%s' to squadronId='%s': already exists",
              request.userId(), request.squadronId());
      throw new ConflictException(message);
    }

    return assignment.getId();
  }

  private static SquadronAssignmentDto toDto(SquadronAssignment assignment) {
    return new SquadronAssignmentDto(
        assignment.getId(),
        assignment.getUserId(),
        assignment.getSquadronId(),
        Set.of(), // TODO: include guest squadron assignment
        assignment.getRole());
  }
}
