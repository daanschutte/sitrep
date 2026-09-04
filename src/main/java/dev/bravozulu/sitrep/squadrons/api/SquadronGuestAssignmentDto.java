package dev.bravozulu.sitrep.squadrons.api;

import java.util.UUID;

public record SquadronGuestAssignmentDto(UUID userId, UUID squadronId, SquadronRole role) {}
