package com.camelbytes.sitrep.squadrons.internal.assignment;

import com.camelbytes.sitrep.squadrons.api.SquadronAssignmentDto;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/squadrons")
@Validated
public class SquadronAssignmentController {
  private final SquadronAssignmentService service;

  public SquadronAssignmentController(SquadronAssignmentService service) {
    this.service = service;
  }

  @GetMapping("/assignments")
  public SquadronAssignmentDto getByUserId(@RequestParam UUID userId) {
    return service.getSquadronAssignmentByUserId(userId);
  }

  @GetMapping("/{squadronId}/assignments")
  public List<SquadronAssignmentDto> getBySquadronId(@PathVariable UUID squadronId) {
    return service.getCurrentSquadronAssignmentsBySquadronId(squadronId);
  }

  @PostMapping("/{squadronId}/assignments")
  public ResponseEntity<Void> createSquadronAssignment(
      @PathVariable UUID squadronId, @RequestBody @Valid SquadronAssignmentCreateRequest request) {
    service.createSquadronAssignment(squadronId, request);
    return ResponseEntity.created(URI.create("/api/v1/squadrons/" + squadronId + "/assignments"))
        .build();
  }
}
