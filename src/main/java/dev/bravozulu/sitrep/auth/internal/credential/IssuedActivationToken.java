package dev.bravozulu.sitrep.auth.internal.credential;

import java.time.Instant;

public record IssuedActivationToken(String token, Instant expiresAt) {
  @Override
  public String toString() {
    return "IssuedActivationToken[token=<redacted>, expiresAt=" + expiresAt + "]";
  }
}
