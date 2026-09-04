package dev.bravozulu.sitrep.squadrons.api;

import java.util.List;
import java.util.UUID;

public record UserSquadronAccessDto(
        UUID primarySquadronId, UUID userId,
        SquadronRole role,
        List<SquadronGuestAssignmentDto> guestAssignmentDtos) {}
