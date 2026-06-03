package com.camelbytes.sitrep.squadrons.internal;

import com.camelbytes.sitrep.shared.exceptions.ConflictException;
import com.camelbytes.sitrep.squadrons.api.SquadronDto;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

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
    return new SquadronDto(
        squadron.getId(), squadron.getName(), squadron.getShortName(), squadron.isActive());
  }

  public UUID createSquadron(SquadronCreateRequest request) {
    Squadron squadron = new Squadron(request.name(), request.shortName());
    try {
      squadron = repository.save(squadron);
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(
          "Could not create squadron: Squadron name ("
              + request.name()
              + ") or short name ("
              + request.shortName()
              + ") already exists");
    }
    return squadron.getId();
  }
}
