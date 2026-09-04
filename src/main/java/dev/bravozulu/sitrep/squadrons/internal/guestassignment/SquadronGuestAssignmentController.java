package dev.bravozulu.sitrep.squadrons.internal.guestassignment;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/squadrons")
@Validated
public class SquadronGuestAssignmentController {
  private final SquadronGuestAssignmentService service;

  public SquadronGuestAssignmentController(SquadronGuestAssignmentService service) {
    this.service = service;
  }

  @PostMapping("/{squadronId}/guest-assignments")
  public ResponseEntity<Void> createSquadronGuestAssignment(
      @PathVariable UUID squadronId,
      @RequestBody @Valid SquadronGuestAssignmentCreateRequest request) {
    service.createSquadronGuestAssignment(squadronId, request);
    return ResponseEntity.created(
            URI.create("/api/v1/squadrons/" + squadronId + "/guest-assignments"))
        .build();
  }

  @PutMapping("/{squadronId}/guest-assignments/{userId}/revoke")
  public ResponseEntity<Void> revokeSquadronGuestAssignment(
      @PathVariable UUID squadronId, @PathVariable UUID userId) {
    service.revokeSquadronGuestAssignment(squadronId, userId);
    return ResponseEntity.noContent().build();
  }
}
