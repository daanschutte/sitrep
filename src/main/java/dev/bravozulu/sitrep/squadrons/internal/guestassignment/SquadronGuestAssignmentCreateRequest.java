package dev.bravozulu.sitrep.squadrons.internal.guestassignment;

import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SquadronGuestAssignmentCreateRequest(
    @NotNull UUID userId, @NotNull SquadronRole role) {}
