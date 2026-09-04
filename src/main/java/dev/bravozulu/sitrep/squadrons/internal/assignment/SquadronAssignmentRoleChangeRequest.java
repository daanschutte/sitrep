package dev.bravozulu.sitrep.squadrons.internal.assignment;

import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import jakarta.validation.constraints.NotNull;

public record SquadronAssignmentRoleChangeRequest(@NotNull SquadronRole role) {}
