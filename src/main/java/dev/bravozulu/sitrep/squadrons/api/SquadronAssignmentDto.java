package dev.bravozulu.sitrep.squadrons.api;

import java.util.UUID;

public record SquadronAssignmentDto(UUID squadronId, UUID userId, SquadronRole role) {}
