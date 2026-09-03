package dev.bravozulu.sitrep.squadrons.internal.guestassignment;

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
@Table(name = "squadron_guest_assignment")
public class SquadronGuestAssignment extends BaseEntity {
  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private UUID squadronId;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private SquadronRole role;

  private Instant revokedAt;

  protected SquadronGuestAssignment() {}

  public SquadronGuestAssignment(UUID userId, UUID squadronId, SquadronRole role) {
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

  public void setRole(SquadronRole role) {
    this.role = role;
  }

  public boolean isActive() {
    return revokedAt == null;
  }

  public void revokeAccess(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }
}
