package com.camelbytes.sitrep.squadrons.internal.assignment;

import com.camelbytes.sitrep.shared.exceptions.ConflictException;
import com.camelbytes.sitrep.squadrons.api.SquadronAssignmentDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class SquadronAssignmentService {
  private static final Logger log = LoggerFactory.getLogger(SquadronAssignmentService.class);

  private final SquadronAssignmentRepository repository;

  public SquadronAssignmentService(SquadronAssignmentRepository repository) {
    this.repository = repository;
  }

  public SquadronAssignmentDto getSquadronAssignmentByUserId(UUID userId) {
    // TODO: include cross-squadron assignment

    return repository
        .findSquadronAssignmentByUserId(userId)
        .map(SquadronAssignmentService::toDto)
        .orElseThrow(() -> new SquadronAssignmentNotFoundException(userId));
  }

  public UUID createSquadronAssignment(SquadronAssignmentCreateRequest request) {
    SquadronAssignment assignment =
        new SquadronAssignment(request.userId(), request.squadronId(), request.role());

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
        Set.of(), // TODO: include cross-squadron assignment
        assignment.getRole());
  }
}
