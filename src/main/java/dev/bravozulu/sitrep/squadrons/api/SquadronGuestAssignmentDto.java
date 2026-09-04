package dev.bravozulu.sitrep.squadrons.api;

import java.util.UUID;

public record SquadronGuestAssignmentDto(UUID squadronId, UUID userId, SquadronRole role) {}
