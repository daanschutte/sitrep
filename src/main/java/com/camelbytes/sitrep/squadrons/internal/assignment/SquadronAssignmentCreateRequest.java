package com.camelbytes.sitrep.squadrons.internal.assignment;

import com.camelbytes.sitrep.squadrons.api.SquadronRole;
import java.util.UUID;

public record SquadronAssignmentCreateRequest(UUID userId, SquadronRole role) {}
