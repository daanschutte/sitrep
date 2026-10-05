package dev.bravozulu.sitrep.auth.internal.credential;

import dev.bravozulu.sitrep.auth.internal.SecureTokens;
import dev.bravozulu.sitrep.auth.internal.credential.RedemptionOutcome.Redeemed;
import dev.bravozulu.sitrep.auth.internal.credential.RedemptionOutcome.Rejected;
import dev.bravozulu.sitrep.users.api.UserQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deliberately not {@code @Transactional}: a rejected redemption must still commit the token's
 * revocation (D8), so the attempt and the revocation run in separate programmatic transactions.
 */
@Service
public class CredentialService {
  private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

  private final ActivationTokenRepository tokenRepository;
  private final CredentialRepository credentialRepository;
  private final UserQueryService userQueryService;
  private final PasswordEncoder passwordEncoder;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public CredentialService(
      ActivationTokenRepository tokenRepository,
      CredentialRepository credentialRepository,
      UserQueryService userQueryService,
      PasswordEncoder passwordEncoder,
      TransactionTemplate transactionTemplate,
      Clock clock) {
    this.tokenRepository = tokenRepository;
    this.credentialRepository = credentialRepository;
    this.userQueryService = userQueryService;
    this.passwordEncoder = passwordEncoder;
    this.transactionTemplate = transactionTemplate;
    this.clock = clock;
  }

  public void redeem(String rawToken, String password) {
    String tokenHash = SecureTokens.sha256Hex(rawToken);

    switch (attempt(tokenHash, password)) {
      case Redeemed(UUID tokenId, UUID userId) ->
          log.info("Credential created for userId={} via activation tokenId={}", userId, tokenId);
      case Rejected(RejectionReason reason, UUID tokenId, UUID userId) -> {
        if (reason.revokesToken()) {
          transactionTemplate.executeWithoutResult(_ -> revoke(tokenHash));
        }
        log.warn(
            "Activation token redemption rejected: reason={} tokenId={} userId={}",
            reason,
            tokenId,
            userId);
        throw new InvalidActivationTokenException();
      }
    }
  }

  private RedemptionOutcome attempt(String tokenHash, String password) {
    try {
      return transactionTemplate.execute(_ -> redeemInTransaction(tokenHash, password));
    } catch (DataIntegrityViolationException ex) {
      // Lost a concurrent redemption: another request created this user's credential first.
      return rejectedAfterRollback(tokenHash, RejectionReason.CREDENTIAL_EXISTS);
    } catch (OptimisticLockingFailureException ex) {
      // A concurrent issue() revoked this token while it was being consumed.
      return rejectedAfterRollback(tokenHash, RejectionReason.REVOKED);
    }
  }

  private Rejected rejectedAfterRollback(String tokenHash, RejectionReason reason) {
    return tokenRepository
        .findByTokenHash(tokenHash)
        .map(token -> new Rejected(reason, token.getId(), token.getUserId()))
        .orElseGet(() -> new Rejected(reason, null, null));
  }

  private RedemptionOutcome redeemInTransaction(String tokenHash, String password) {
    Optional<ActivationToken> found = tokenRepository.findByTokenHash(tokenHash);
    if (found.isEmpty()) {
      return new Rejected(RejectionReason.UNKNOWN, null, null);
    }

    ActivationToken token = found.get();
    UUID tokenId = token.getId();
    UUID userId = token.getUserId();
    Instant now = clock.instant();

    TokenStatus status = token.status(now);
    if (status != TokenStatus.REDEEMABLE) {
      return new Rejected(RejectionReason.of(status), tokenId, userId);
    }
    if (!userQueryService.isActiveUser(userId)) {
      return new Rejected(RejectionReason.USER_INACTIVE, tokenId, userId);
    }
    if (credentialRepository.existsByUserId(userId)) {
      return new Rejected(RejectionReason.CREDENTIAL_EXISTS, tokenId, userId);
    }

    // Hashed only after every token check, so unauthenticated junk tokens never cost a BCrypt run.
    String passwordHash = passwordEncoder.encode(password);
    credentialRepository.saveAndFlush(new Credential(userId, passwordHash, token.getRole()));
    token.consume(now);
    return new Redeemed(tokenId, userId);
  }

  private void revoke(String tokenHash) {
    tokenRepository.findByTokenHash(tokenHash).ifPresent(token -> token.revoke(clock.instant()));
  }
}
