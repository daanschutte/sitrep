package com.camelbytes.sitrep.users.internal;

public record UserCreateRequest(String firstName, String lastName, String email, String rank) {}
