package dev.bravozulu.sitrep.squadrons.internal.guestaccess;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import dev.bravozulu.sitrep.squadrons.api.SquadronGuestAssignmentDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronQueryService;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentService;
import dev.bravozulu.sitrep.users.api.UserQueryService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SquadronGuestAccessService {
  private static final Logger log = LoggerFactory.getLogger(SquadronGuestAccessService.class);

  private final SquadronAssignmentService squadronAssignmentService;
  private final SquadronGuestAccessRepository repository;
  private final SquadronQueryService squadronQueryService;
  private final UserQueryService userQueryService;

  public SquadronGuestAccessService(
      SquadronGuestAccessRepository repository,
      SquadronAssignmentService squadronAssignmentService,
      SquadronQueryService squadronQueryService,
      UserQueryService userQueryService) {
    this.repository = repository;
    this.squadronAssignmentService = squadronAssignmentService;
    this.squadronQueryService = squadronQueryService;
    this.userQueryService = userQueryService;
  }

  public Optional<SquadronGuestAccess> findSquadronGuestAccess(UUID squadronId, UUID userId) {
    return repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId);
  }

  public List<SquadronGuestAssignmentDto> getSquadronGuestAccess(UUID userId) {
    return repository.findAllByUserIdAndRevokedAtIsNull(userId).stream().map(this::toDto).toList();
  }

  @Transactional
  public void createSquadronGuestAccess(UUID squadronId, SquadronGuestAccessCreateRequest request) {
    squadronQueryService.validateSquadronExists(squadronId);
    userQueryService.validateUserExists(request.userId());

    if (squadronAssignmentService.isUserPrimarySquadron(squadronId, request.userId())) {
      log.error(
          "Could not assign guest access: squadronId={} already primary squadron of userId={}",
          squadronId,
          request.userId().toString());
      return;
    }

    findSquadronGuestAccess(squadronId, request.userId())
        .ifPresentOrElse(
            existing -> {
              if (existing.getRole() != request.role()) {
                existing.setRole(request.role());
                repository.saveAndFlush(existing);
              } else {
                log.debug(
                    "userId={} already has guest access to squadronId={} with role={}",
                    request.userId().toString(),
                    squadronId.toString(),
                    request.role());
              }
            },
            () -> {
              SquadronGuestAccess access =
                  new SquadronGuestAccess(squadronId, request.userId(), request.role());
              try {
                access = repository.save(access);
                log.debug(
                    "Squadron guest access with id={} created ({}:{}) in role={}",
                    access.getId(),
                    access.getSquadronId(),
                    access.getUserId(),
                    access.getRole());
              } catch (DataIntegrityViolationException exception) {
                String message =
                    String.format(
                        "Could not guest assign userID='%s' to squadron='%s'",
                        request.userId(), squadronId);
                throw new ConflictException(message);
              }
            });
  }

  @Transactional
  public void revokeSquadronGuestAccess(UUID squadronId, UUID userId) {
    SquadronGuestAccess guestAccess =
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

    guestAccess.revokeAccess(Instant.now());
  }

  private SquadronGuestAssignmentDto toDto(SquadronGuestAccess guestAssignment) {
    return new SquadronGuestAssignmentDto(
        guestAssignment.getId(), guestAssignment.getSquadronId(), guestAssignment.getRole());
  }
}
