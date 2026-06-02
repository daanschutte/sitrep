package com.camelbytes.sitrep.squadrons.internal;

import com.camelbytes.sitrep.squadrons.api.SquadronDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SquadronService {
  private final SquadronRepository repository;

  public SquadronService(SquadronRepository repository) {
    this.repository = repository;
  }

  public SquadronDto getById(UUID id) {
    return repository
        .findById(id)
        .map(SquadronService::toDto)
        .orElseThrow(() -> new SquadronNotFoundException(id));
  }

  private static SquadronDto toDto(Squadron squadron) {
    return new SquadronDto(squadron.getName(), squadron.getShortName(), squadron.isActive());
  }
}
