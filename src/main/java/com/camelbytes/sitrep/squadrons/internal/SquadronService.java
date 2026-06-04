package com.camelbytes.sitrep.squadrons.internal;

import com.camelbytes.sitrep.shared.exceptions.ConflictException;
import com.camelbytes.sitrep.squadrons.api.SquadronDto;
import jakarta.transaction.Transactional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class SquadronService {
  private static final Logger log = LoggerFactory.getLogger(SquadronService.class);

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

  public UUID createSquadron(SquadronCreateRequest request) {
    Squadron squadron = new Squadron(request.name(), request.shortName());
    try {
      squadron = repository.save(squadron);
      log.debug("Squadron with id={} created", squadron.getId());
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

  @Transactional
  public void enableSquadron(UUID id) throws SquadronNotFoundException {
    log.debug("Enabling squadron with id={}", id);
    Squadron squadron =
        repository.findById(id).orElseThrow(() -> new SquadronNotFoundException(id));

    squadron.enable();
    repository.save(squadron);
    log.debug("Squadron {} with id={} enabled", squadron.getName(), squadron.getId());
  }

  @Transactional
  public void disableSquadron(UUID id) throws SquadronNotFoundException {
    log.debug("Disabling squadron with id={}", id);
    Squadron squadron =
        repository.findById(id).orElseThrow(() -> new SquadronNotFoundException(id));

    squadron.disable();
    repository.save(squadron);
    log.debug("Squadron {} with id={} disabled", squadron.getName(), squadron.getId());
  }

  private static SquadronDto toDto(Squadron squadron) {
    return new SquadronDto(
        squadron.getId(), squadron.getName(), squadron.getShortName(), squadron.isActive());
  }
}
