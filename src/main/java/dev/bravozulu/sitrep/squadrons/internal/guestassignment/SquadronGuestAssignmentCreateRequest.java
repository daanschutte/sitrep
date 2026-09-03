package dev.bravozulu.sitrep.squadrons.internal.guestassignment;

import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record SquadronGuestAssignmentCreateRequest(@NotNull UUID userId, @NonNull SquadronRole role) {}
