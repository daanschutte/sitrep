package dev.bravozulu.sitrep.unit.auth.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.bravozulu.sitrep.auth.internal.AuthConfig;
import dev.bravozulu.sitrep.auth.internal.SecureTokens;
import dev.bravozulu.sitrep.auth.internal.credential.ActivationToken;
import dev.bravozulu.sitrep.auth.internal.credential.ActivationTokenRepository;
import dev.bravozulu.sitrep.auth.internal.credential.Credential;
import dev.bravozulu.sitrep.auth.internal.credential.CredentialRepository;
import dev.bravozulu.sitrep.auth.internal.credential.CredentialService;
import dev.bravozulu.sitrep.auth.internal.credential.InvalidActivationTokenException;
import dev.bravozulu.sitrep.auth.internal.credential.SystemRole;
import dev.bravozulu.sitrep.users.api.UserQueryService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class CredentialServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final String RAW_TOKEN = "raw-token";
  private static final String TOKEN_HASH = SecureTokens.sha256Hex(RAW_TOKEN);
  private static final String PASSWORD = "correct horse battery staple";

  @Mock ActivationTokenRepository tokenRepository;
  @Mock CredentialRepository credentialRepository;
  @Mock UserQueryService userQueryService;
  @Mock PlatformTransactionManager transactionManager;

  CredentialService service;
  UUID userId;
  ActivationToken token;

  @BeforeEach
  void setUp() {
    service =
        new CredentialService(
            tokenRepository,
            credentialRepository,
            userQueryService,
            new AuthConfig().passwordEncoder(),
            new TransactionTemplate(transactionManager),
            Clock.fixed(NOW, ZoneOffset.UTC));
    userId = UUID.randomUUID();
    token =
        new ActivationToken(userId, TOKEN_HASH, SystemRole.ADMIN, NOW.plus(Duration.ofHours(1)));
  }

  @Nested
  class Redeem {
    @Test
    void redeem_validToken_createsCredentialAndConsumesToken() {
      givenRedeemableTokenForActiveUser();
      when(credentialRepository.existsByUserId(userId)).thenReturn(false);

      service.redeem(RAW_TOKEN, PASSWORD);

      ArgumentCaptor<Credential> saved = ArgumentCaptor.forClass(Credential.class);
      verify(credentialRepository).saveAndFlush(saved.capture());
      assertThat(saved.getValue().getUserId()).isEqualTo(userId);
      assertThat(saved.getValue().getRole()).isEqualTo(SystemRole.ADMIN);
      assertThat(saved.getValue().getPasswordHash()).startsWith("{bcrypt}$2a$12$");
      assertThat(token.getConsumedAt()).isEqualTo(NOW);
      assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    void redeem_unknownToken_throwsWithoutRevoking() {
      when(tokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.redeem(RAW_TOKEN, PASSWORD))
          .isExactlyInstanceOf(InvalidActivationTokenException.class);
      verify(transactionManager, times(1)).commit(any());
    }

    @Test
    void redeem_expiredToken_throwsWithoutRevoking() {
      ActivationToken expired =
          new ActivationToken(userId, TOKEN_HASH, SystemRole.USER, NOW.minusSeconds(1));
      when(tokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(expired));

      assertThatThrownBy(() -> service.redeem(RAW_TOKEN, PASSWORD))
          .isExactlyInstanceOf(InvalidActivationTokenException.class);
      assertThat(expired.getRevokedAt()).isNull();
      verify(credentialRepository, never()).saveAndFlush(any());
    }

    @Test
    void redeem_consumedToken_throwsWithoutRevoking() {
      token.consume(NOW.minusSeconds(60));
      when(tokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));

      assertThatThrownBy(() -> service.redeem(RAW_TOKEN, PASSWORD))
          .isExactlyInstanceOf(InvalidActivationTokenException.class);
      assertThat(token.getRevokedAt()).isNull();
    }

    @Test
    void redeem_revokedToken_throws() {
      token.revoke(NOW.minusSeconds(60));
      when(tokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));

      assertThatThrownBy(() -> service.redeem(RAW_TOKEN, PASSWORD))
          .isExactlyInstanceOf(InvalidActivationTokenException.class);
      verify(credentialRepository, never()).saveAndFlush(any());
    }

    @Test
    void redeem_deactivatedUser_throwsAndRevokesToken() {
      when(tokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));
      when(userQueryService.isActiveUser(userId)).thenReturn(false);

      assertThatThrownBy(() -> service.redeem(RAW_TOKEN, PASSWORD))
          .isExactlyInstanceOf(InvalidActivationTokenException.class);
      assertThat(token.getRevokedAt()).isEqualTo(NOW);
      assertThat(token.getConsumedAt()).isNull();
      verify(credentialRepository, never()).saveAndFlush(any());
      verify(transactionManager, times(2)).commit(any());
    }

    @Test
    void redeem_credentialAlreadyExists_throwsAndRevokesToken() {
      givenRedeemableTokenForActiveUser();
      when(credentialRepository.existsByUserId(userId)).thenReturn(true);

      assertThatThrownBy(() -> service.redeem(RAW_TOKEN, PASSWORD))
          .isExactlyInstanceOf(InvalidActivationTokenException.class);
      assertThat(token.getRevokedAt()).isEqualTo(NOW);
      verify(credentialRepository, never()).saveAndFlush(any());
    }

    @Test
    void redeem_lostConcurrentRedemption_throwsAndRevokesTokenInSeparateTransaction() {
      givenRedeemableTokenForActiveUser();
      when(credentialRepository.existsByUserId(userId)).thenReturn(false);
      when(credentialRepository.saveAndFlush(any()))
          .thenThrow(new DataIntegrityViolationException("duplicate user_id"));

      assertThatThrownBy(() -> service.redeem(RAW_TOKEN, PASSWORD))
          .isExactlyInstanceOf(InvalidActivationTokenException.class);
      assertThat(token.getRevokedAt()).isEqualTo(NOW);
      assertThat(token.getConsumedAt()).isNull();
      verify(transactionManager).rollback(any());
      verify(transactionManager).commit(any());
    }

    @Test
    void redeem_tokenRevokedConcurrently_throwsWithoutRevokingAgain() {
      givenRedeemableTokenForActiveUser();
      when(credentialRepository.existsByUserId(userId)).thenReturn(false);
      doThrow(new ObjectOptimisticLockingFailureException(ActivationToken.class, UUID.randomUUID()))
          .when(transactionManager)
          .commit(any());

      assertThatThrownBy(() -> service.redeem(RAW_TOKEN, PASSWORD))
          .isExactlyInstanceOf(InvalidActivationTokenException.class);
      assertThat(token.getRevokedAt()).isNull();
      verify(transactionManager, times(1)).commit(any());
    }
  }

  private void givenRedeemableTokenForActiveUser() {
    when(tokenRepository.findByTokenHash(TOKEN_HASH)).thenReturn(Optional.of(token));
    when(userQueryService.isActiveUser(userId)).thenReturn(true);
  }
}
