package dev.bravozulu.sitrep.unit.auth.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.bravozulu.sitrep.auth.internal.AuthProperties;
import dev.bravozulu.sitrep.auth.internal.SecureTokens;
import dev.bravozulu.sitrep.auth.internal.credential.ActivationToken;
import dev.bravozulu.sitrep.auth.internal.credential.ActivationTokenRepository;
import dev.bravozulu.sitrep.auth.internal.credential.ActivationTokenService;
import dev.bravozulu.sitrep.auth.internal.credential.CredentialRepository;
import dev.bravozulu.sitrep.auth.internal.credential.IssuedActivationToken;
import dev.bravozulu.sitrep.auth.internal.credential.SystemRole;
import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import dev.bravozulu.sitrep.users.internal.User;
import dev.bravozulu.sitrep.users.internal.UserNotActiveException;
import dev.bravozulu.sitrep.users.internal.UserNotFoundException;
import dev.bravozulu.sitrep.users.internal.UserRepository;
import dev.bravozulu.sitrep.users.internal.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

@ExtendWith(MockitoExtension.class)
class ActivationTokenServiceTest {
  private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
  private static final Duration TTL = Duration.ofHours(48);

  @Mock ActivationTokenRepository repository;
  @Mock CredentialRepository credentialRepository;
  @Mock UserRepository userRepository;

  ActivationTokenService service;
  UUID userId;

  @BeforeEach
  void setUp() {
    service =
        new ActivationTokenService(
            repository,
            credentialRepository,
            new UserService(userRepository),
            new AuthProperties(TTL),
            Clock.fixed(NOW, ZoneOffset.UTC));
    userId = UUID.randomUUID();
  }

  @Nested
  class Issue {
    @Test
    void issue_persistsHashAndReturnsRawToken() {
      givenActiveUserWithoutCredential();
      when(repository.findAllByUserIdAndConsumedAtIsNullAndRevokedAtIsNull(userId))
          .thenReturn(List.of());
      when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

      IssuedActivationToken issued = service.issue(userId, SystemRole.ADMIN);

      ArgumentCaptor<ActivationToken> saved = ArgumentCaptor.forClass(ActivationToken.class);
      verify(repository).saveAndFlush(saved.capture());
      assertThat(saved.getValue().getTokenHash())
          .isEqualTo(SecureTokens.sha256Hex(issued.token()))
          .isNotEqualTo(issued.token());
      assertThat(saved.getValue().getUserId()).isEqualTo(userId);
      assertThat(saved.getValue().getRole()).isEqualTo(SystemRole.ADMIN);
      assertThat(issued.expiresAt()).isEqualTo(NOW.plus(TTL));
    }

    @Test
    void issue_existingLiveTokens_revokesThemBeforeInsert() {
      givenActiveUserWithoutCredential();
      ActivationToken older =
          new ActivationToken(userId, "older", SystemRole.USER, NOW.plus(Duration.ofHours(1)));
      when(repository.findAllByUserIdAndConsumedAtIsNullAndRevokedAtIsNull(userId))
          .thenReturn(List.of(older));
      when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

      service.issue(userId, SystemRole.USER);

      assertThat(older.getRevokedAt()).isEqualTo(NOW);
      verify(repository).flush();
    }

    @Test
    void issue_unknownUser_throwsUserNotFoundException() {
      when(userRepository.findById(userId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.issue(userId, SystemRole.USER))
          .isExactlyInstanceOf(UserNotFoundException.class);
      verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void issue_deactivatedUser_throwsUserNotActiveException() {
      User user = new User("Chuck", "Yeager", "sonic@boom.com", "Gen");
      user.deactivate();
      when(userRepository.findById(userId)).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> service.issue(userId, SystemRole.USER))
          .isExactlyInstanceOf(UserNotActiveException.class);
      verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void issue_userAlreadyHasCredential_throwsConflictException() {
      when(userRepository.findById(userId))
          .thenReturn(Optional.of(new User("Chuck", "Yeager", "sonic@boom.com", "Gen")));
      when(credentialRepository.existsByUserId(userId)).thenReturn(true);

      assertThatThrownBy(() -> service.issue(userId, SystemRole.USER))
          .isExactlyInstanceOf(ConflictException.class)
          .hasMessageContaining(userId.toString());
      verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void issue_concurrentRevocationOfSameToken_throwsConflictException() {
      givenActiveUserWithoutCredential();
      ActivationToken older =
          new ActivationToken(userId, "older", SystemRole.USER, NOW.plus(Duration.ofHours(1)));
      when(repository.findAllByUserIdAndConsumedAtIsNullAndRevokedAtIsNull(userId))
          .thenReturn(List.of(older));
      doThrow(new ObjectOptimisticLockingFailureException(ActivationToken.class, UUID.randomUUID()))
          .when(repository)
          .flush();

      assertThatThrownBy(() -> service.issue(userId, SystemRole.USER))
          .isExactlyInstanceOf(ConflictException.class);
      verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void issue_concurrentIssueViolatesLiveTokenIndex_throwsConflictException() {
      givenActiveUserWithoutCredential();
      when(repository.findAllByUserIdAndConsumedAtIsNullAndRevokedAtIsNull(userId))
          .thenReturn(List.of());
      when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(""));

      assertThatThrownBy(() -> service.issue(userId, SystemRole.USER))
          .isExactlyInstanceOf(ConflictException.class);
    }
  }

  private void givenActiveUserWithoutCredential() {
    when(userRepository.findById(userId))
        .thenReturn(Optional.of(new User("Chuck", "Yeager", "sonic@boom.com", "Gen")));
    when(credentialRepository.existsByUserId(userId)).thenReturn(false);
  }
}
