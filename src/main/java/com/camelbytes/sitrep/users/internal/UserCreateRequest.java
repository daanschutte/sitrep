package com.camelbytes.sitrep.users.internal;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserCreateRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @Email String email,
    @NotBlank String rank) {}
