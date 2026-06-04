package com.camelbytes.sitrep.squadrons.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SquadronCreateRequest(@NotBlank String name, @Size(min = 1) String shortName) {}
