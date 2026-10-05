package dev.bravozulu.sitrep.auth.internal.credential;

import java.util.UUID;

sealed interface RedemptionOutcome permits RedemptionOutcome.Redeemed, RedemptionOutcome.Rejected {
  record Redeemed(UUID tokenId, UUID userId) implements RedemptionOutcome {}

  record Rejected(RejectionReason reason, UUID tokenId, UUID userId) implements RedemptionOutcome {}
}
