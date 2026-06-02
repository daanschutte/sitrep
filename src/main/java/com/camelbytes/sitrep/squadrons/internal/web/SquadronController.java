package com.camelbytes.sitrep.squadrons.internal.web;

import com.camelbytes.sitrep.squadrons.api.SquadronDto;
import com.camelbytes.sitrep.squadrons.internal.SquadronService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

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
}
