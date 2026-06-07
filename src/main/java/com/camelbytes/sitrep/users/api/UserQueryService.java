package com.camelbytes.sitrep.users.api;

import org.springframework.modulith.NamedInterface;

import java.util.UUID;

@NamedInterface
public interface UserQueryService {
  void validateUserExists(UUID userId);
}
