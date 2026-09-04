package dev.bravozulu.sitrep.squadrons.internal.guestassignment;

import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import jakarta.validation.constraints.NotNull;

public record SquadronGuestAssignmentRoleChangeRequest(@NotNull SquadronRole role) {}
