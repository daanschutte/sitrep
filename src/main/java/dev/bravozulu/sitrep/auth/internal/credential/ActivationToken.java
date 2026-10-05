package dev.bravozulu.sitrep.auth.internal.credential;

import dev.bravozulu.sitrep.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activation_token")
public class ActivationToken extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID userId;

  @Column(nullable = false, updatable = false)
  private String tokenHash;

  @Column(nullable = false, updatable = false)
  @Enumerated(EnumType.STRING)
  private SystemRole role;

  @Column(nullable = false, updatable = false)
  private Instant expiresAt;

  private Instant consumedAt;

  private Instant revokedAt;

  protected ActivationToken() {}

  public ActivationToken(UUID userId, String tokenHash, SystemRole role, Instant expiresAt) {
    this.userId = userId;
    this.tokenHash = tokenHash;
    this.role = role;
    this.expiresAt = expiresAt;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public SystemRole getRole() {
    return role;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getConsumedAt() {
    return consumedAt;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public TokenStatus status(Instant now) {
    if (consumedAt != null) {
      return TokenStatus.CONSUMED;
    }
    if (revokedAt != null) {
      return TokenStatus.REVOKED;
    }
    if (!now.isBefore(expiresAt)) {
      return TokenStatus.EXPIRED;
    }
    return TokenStatus.REDEEMABLE;
  }

  public void consume(Instant now) {
    this.consumedAt = now;
  }

  public void revoke(Instant now) {
    this.revokedAt = now;
  }
}
