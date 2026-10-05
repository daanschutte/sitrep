package dev.bravozulu.sitrep.auth.internal.credential;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {
  boolean existsByUserId(UUID userId);
}
