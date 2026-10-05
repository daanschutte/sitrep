package dev.bravozulu.sitrep.users.api;

import java.util.Optional;
import java.util.UUID;

public interface UserQueryService {
  void validateActiveUserExists(UUID userId);

  boolean isActiveUser(UUID userId);

  Optional<UUID> findActiveUserIdByEmail(String email);
}
