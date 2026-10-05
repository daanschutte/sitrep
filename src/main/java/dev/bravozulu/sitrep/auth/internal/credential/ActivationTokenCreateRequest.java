package dev.bravozulu.sitrep.auth.internal.credential;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ActivationTokenCreateRequest(@NotNull UUID userId, @NotNull SystemRole role) {}
