package com.camelbytes.sitrep.users.api;

import java.util.UUID;

public record UserDto(UUID id, String firstName, String lastName, String email, String rank) {}
