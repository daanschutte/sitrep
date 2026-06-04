package com.camelbytes.sitrep.squadrons.internal.web;

import com.camelbytes.sitrep.squadrons.api.SquadronDto;
import com.camelbytes.sitrep.squadrons.internal.squadron.SquadronCreateRequest;
import com.camelbytes.sitrep.squadrons.internal.squadron.SquadronService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/squadrons")
@Validated
public class SquadronController {
  private final SquadronService service;

  public SquadronController(SquadronService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  public SquadronDto getSquadronById(@PathVariable UUID id) {
    return service.getById(id);
  }

  @PostMapping
  public ResponseEntity<Void> createSquadron(@RequestBody @Valid SquadronCreateRequest request) {
    UUID createdId = service.createSquadron(request);
    return ResponseEntity.created(URI.create("/api/v1/squadrons/" + createdId)).build();
  }

  @PutMapping("/{id}/enable")
  public ResponseEntity<Void> enableSquadron(@PathVariable UUID id) {
    service.enableSquadron(id);
    return ResponseEntity.noContent().build();
  }

  @PutMapping("/{id}/disable")
  public ResponseEntity<Void> disableSquadron(@PathVariable UUID id) {
    service.disableSquadron(id);
    return ResponseEntity.noContent().build();
  }
}
