package dev.bravozulu.sitrep.auth.internal.credential;

enum RejectionReason {
  UNKNOWN(false),
  EXPIRED(false),
  CONSUMED(false),
  REVOKED(false),
  USER_INACTIVE(true),
  CREDENTIAL_EXISTS(true);

  private final boolean revokesToken;

  RejectionReason(boolean revokesToken) {
    this.revokesToken = revokesToken;
  }

  boolean revokesToken() {
    return revokesToken;
  }

  static RejectionReason of(TokenStatus status) {
    return switch (status) {
      case EXPIRED -> EXPIRED;
      case CONSUMED -> CONSUMED;
      case REVOKED -> REVOKED;
      case REDEEMABLE -> throw new IllegalArgumentException("A redeemable token is not rejected");
    };
  }
}
