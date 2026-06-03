package com.camelbytes.sitrep.squadrons.internal;

import jakarta.validation.constraints.NotBlank;

public record SquadronCreateRequest(@NotBlank String name, String shortName) {}
