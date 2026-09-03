package dev.bravozulu.sitrep.squadrons.internal.guestaccess;

import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record SquadronGuestAccessCreateRequest(@NotNull UUID userId, @NonNull SquadronRole role) {}
