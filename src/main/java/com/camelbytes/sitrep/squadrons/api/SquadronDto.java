package com.camelbytes.sitrep.squadrons.api;

import java.util.Optional;

public record SquadronDto(String name, Optional<String> shortName, boolean isActive) {}
