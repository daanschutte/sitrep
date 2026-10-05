package dev.bravozulu.sitrep.unit.auth.credential;

import static org.assertj.core.api.Assertions.assertThat;

import dev.bravozulu.sitrep.auth.internal.credential.ActivationToken;
import dev.bravozulu.sitrep.auth.internal.credential.SystemRole;
import dev.bravozulu.sitrep.auth.internal.credential.TokenStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActivationTokenTest {
  private static final Instant EXPIRES_AT = Instant.parse("2026-10-07T12:00:00Z");

  private final ActivationToken token =
      new ActivationToken(UUID.randomUUID(), "hash", SystemRole.USER, EXPIRES_AT);

  @Test
  void status_beforeExpiry_isRedeemable() {
    assertThat(token.status(EXPIRES_AT.minusMillis(1))).isEqualTo(TokenStatus.REDEEMABLE);
  }

  @Test
  void status_atExpiry_isExpired() {
    assertThat(token.status(EXPIRES_AT)).isEqualTo(TokenStatus.EXPIRED);
  }

  @Test
  void status_consumed_isConsumed() {
    token.consume(EXPIRES_AT.minus(Duration.ofHours(1)));

    assertThat(token.status(EXPIRES_AT.minus(Duration.ofMinutes(30))))
        .isEqualTo(TokenStatus.CONSUMED);
  }

  @Test
  void status_revoked_isRevoked() {
    token.revoke(EXPIRES_AT.minus(Duration.ofHours(1)));

    assertThat(token.status(EXPIRES_AT.minus(Duration.ofMinutes(30))))
        .isEqualTo(TokenStatus.REVOKED);
  }

  @Test
  void status_consumedThenExpired_staysConsumed() {
    token.consume(EXPIRES_AT.minus(Duration.ofHours(1)));

    assertThat(token.status(EXPIRES_AT.plus(Duration.ofDays(1)))).isEqualTo(TokenStatus.CONSUMED);
  }
}
