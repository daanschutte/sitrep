package dev.bravozulu.sitrep.squadrons.api;

import java.util.UUID;

public record SquadronGuestAssignmentDto(UUID id, UUID squadronId, SquadronRole role) {}
