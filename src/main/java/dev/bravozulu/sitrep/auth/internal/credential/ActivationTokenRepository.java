package dev.bravozulu.sitrep.auth.internal.credential;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivationTokenRepository extends JpaRepository<ActivationToken, UUID> {
  Optional<ActivationToken> findByTokenHash(String tokenHash);

  List<ActivationToken> findAllByUserIdAndConsumedAtIsNullAndRevokedAtIsNull(UUID userId);
}
