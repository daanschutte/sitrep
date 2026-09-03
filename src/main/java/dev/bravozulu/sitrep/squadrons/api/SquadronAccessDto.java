package dev.bravozulu.sitrep.squadrons.api;

import java.util.List;
import java.util.UUID;

public record SquadronAccessDto(
    UUID userId,
    UUID primarySquadronId,
    SquadronRole role,
    List<SquadronGuestAssignmentDto> guestAssignmentDtos) {}
