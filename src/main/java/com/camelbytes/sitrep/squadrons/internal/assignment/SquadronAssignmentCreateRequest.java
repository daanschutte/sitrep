package com.camelbytes.sitrep.squadrons.internal.assignment;

import com.camelbytes.sitrep.squadrons.api.SquadronRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SquadronAssignmentCreateRequest(@NotNull UUID userId, @NotNull SquadronRole role) {}
