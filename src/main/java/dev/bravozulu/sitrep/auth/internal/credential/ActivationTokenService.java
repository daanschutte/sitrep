package dev.bravozulu.sitrep.auth.internal.credential;

import dev.bravozulu.sitrep.auth.internal.AuthProperties;
import dev.bravozulu.sitrep.auth.internal.SecureTokens;
import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import dev.bravozulu.sitrep.users.api.UserQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivationTokenService {
  private static final Logger log = LoggerFactory.getLogger(ActivationTokenService.class);

  private final ActivationTokenRepository repository;
  private final CredentialRepository credentialRepository;
  private final UserQueryService userQueryService;
  private final AuthProperties properties;
  private final Clock clock;

  public ActivationTokenService(
      ActivationTokenRepository repository,
      CredentialRepository credentialRepository,
      UserQueryService userQueryService,
      AuthProperties properties,
      Clock clock) {
    this.repository = repository;
    this.credentialRepository = credentialRepository;
    this.userQueryService = userQueryService;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public IssuedActivationToken issue(UUID userId, SystemRole role) {
    userQueryService.validateActiveUserExists(userId);

    if (credentialRepository.existsByUserId(userId)) {
      throw new ConflictException("userId=" + userId + " already has a credential");
    }

    Instant now = clock.instant();
    String rawToken = SecureTokens.generate();
    ActivationToken token =
        new ActivationToken(
            userId,
            SecureTokens.sha256Hex(rawToken),
            role,
            now.plus(properties.activationTokenTtl()));
    try {
      revokeLiveTokens(userId, now);
      token = repository.saveAndFlush(token);
    } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
      // A concurrent issue (live-token index) or redeem (@Version on the old token) got there
      // first.
      throw new ConflictException("Could not issue activation token for userId=" + userId);
    }

    log.info(
        "Activation token id={} issued for userId={} role={} expiresAt={}",
        token.getId(),
        userId,
        role,
        token.getExpiresAt());
    return new IssuedActivationToken(rawToken, token.getExpiresAt());
  }

  private void revokeLiveTokens(UUID userId, Instant now) {
    List<ActivationToken> liveTokens =
        repository.findAllByUserIdAndConsumedAtIsNullAndRevokedAtIsNull(userId);
    if (liveTokens.isEmpty()) {
      return;
    }
    liveTokens.forEach(token -> token.revoke(now));
    // Hibernate flushes inserts before updates; without this the new token's INSERT would hit
    // the one-live-token-per-user partial index before these revocations are written.
    repository.flush();
    log.debug("Revoked {} live activation token(s) for userId={}", liveTokens.size(), userId);
  }
}
