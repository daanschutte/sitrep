package dev.bravozulu.sitrep.squadrons.api;

import java.util.UUID;

public record SquadronAccessDto(UUID userId, UUID squadronId, SquadronRole role) {}
