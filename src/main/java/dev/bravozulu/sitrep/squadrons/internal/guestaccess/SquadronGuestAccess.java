package dev.bravozulu.sitrep.squadrons.internal.guestaccess;

import dev.bravozulu.sitrep.shared.domain.BaseEntity;
import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "squadron_guest_access")
public class SquadronGuestAccess extends BaseEntity {
  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private UUID squadronId;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private SquadronRole role;

  private Instant revokedAt;

  protected SquadronGuestAccess() {}

  public SquadronGuestAccess(UUID userId, UUID squadronId, SquadronRole role) {
    this.userId = userId;
    this.squadronId = squadronId;
    this.role = role;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getSquadronId() {
    return squadronId;
  }

  public SquadronRole getRole() {
    return role;
  }

  public boolean isActive() {
    return revokedAt == null;
  }
}
