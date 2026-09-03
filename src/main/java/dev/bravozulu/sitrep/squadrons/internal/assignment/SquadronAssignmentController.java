package dev.bravozulu.sitrep.squadrons.internal.assignment;

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
  private final SquadronAssignmentService service;

  public SquadronAssignmentController(SquadronAssignmentService service) {
    this.service = service;
  }

  @PostMapping("/{squadronId}/assignments")
  public ResponseEntity<Void> createSquadronAssignment(
      @PathVariable UUID squadronId, @RequestBody @Valid SquadronAssignmentCreateRequest request) {
    service.createSquadronAssignment(squadronId, request);
    return ResponseEntity.created(URI.create("/api/v1/squadrons/" + squadronId + "/assignments"))
        .build();
  }

  @PutMapping("/{squadronId}/assignments/{userId}/revoke")
  public ResponseEntity<Void> revokeSquadronAssignment(
      @PathVariable UUID squadronId, @PathVariable UUID userId) {
    service.revokeSquadronAssignment(squadronId, userId);
    return ResponseEntity.noContent().build();
  }
}
