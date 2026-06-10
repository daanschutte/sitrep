package dev.bravozulu.sitrep.squadrons.api;

import java.util.Optional;
import java.util.UUID;

public record SquadronDto(UUID id, String name, Optional<String> shortName, boolean isActive) {}
