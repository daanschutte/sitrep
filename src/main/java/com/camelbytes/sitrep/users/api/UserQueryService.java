package com.camelbytes.sitrep.users.api;

import java.util.UUID;

public interface UserQueryService {
  void validateUserExists(UUID userId);
}
