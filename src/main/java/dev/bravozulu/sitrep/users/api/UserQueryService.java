package dev.bravozulu.sitrep.users.api;

import java.util.UUID;

public interface UserQueryService {
  void validateActiveUserExists(UUID userId);
}
