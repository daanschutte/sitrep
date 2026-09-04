package dev.bravozulu.sitrep.squadrons.internal.access;

import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentCreateRequest;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentRoleChangeRequest;
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
public class SquadronAssignmentController {
  private final SquadronAccessCoordinator coordinator;

  public SquadronAssignmentController(SquadronAccessCoordinator coordinator) {
    this.coordinator = coordinator;
  }

  @PostMapping("/{squadronId}/assignments")
  public ResponseEntity<Void> assignSquadron(
      @PathVariable UUID squadronId, @RequestBody @Valid SquadronAssignmentCreateRequest request) {
    coordinator.assignSquadron(squadronId, request.userId(), request.role());
    return ResponseEntity.created(URI.create("/api/v1/squadrons/" + squadronId + "/assignments"))
        .build();
  }

  @PutMapping("/{squadronId}/assignments/transfer")
  public ResponseEntity<Void> transferSquadron(
      @PathVariable UUID squadronId, @RequestBody @Valid SquadronAssignmentCreateRequest request) {
    coordinator.transferSquadronAssignment(squadronId, request.userId(), request.role());
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{squadronId}/assignments/{userId}/revoke")
  public ResponseEntity<Void> revokeSquadronAssignment(
      @PathVariable UUID squadronId, @PathVariable UUID userId) {
    coordinator.revokeSquadronAssignment(squadronId, userId);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{squadronId}/assignments/{userId}/role")
  public ResponseEntity<Void> changeSquadronRole(
      @PathVariable UUID squadronId,
      @PathVariable UUID userId,
      @RequestBody @Valid SquadronAssignmentRoleChangeRequest request) {
    coordinator.changeSquadronRole(squadronId, userId, request.role());
    return ResponseEntity.noContent().build();
  }
}
