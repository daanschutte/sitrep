package dev.bravozulu.sitrep.squadrons.api;

import java.util.Set;
import java.util.UUID;

public record SquadronAssignmentDto(
    UUID id, UUID userId, UUID primarySquadronId, Set<UUID> guestSquadronIds, SquadronRole role) {}
