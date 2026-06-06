package com.camelbytes.sitrep.squadrons.internal.assignment;

import com.camelbytes.sitrep.shared.domain.BaseEntity;
import com.camelbytes.sitrep.squadrons.api.SquadronRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "squadron_assignment")
public class SquadronAssignment extends BaseEntity {
  @Column(nullable = false)
  private UUID userId;

  @Column(nullable = false)
  private UUID squadronId;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private SquadronRole role;

  @Column(nullable = false)
  private boolean isCurrent = true;

  private Instant endedAt;

  protected SquadronAssignment() {}

  public SquadronAssignment(UUID userId, UUID squadronId, SquadronRole role) {
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

  public void endAssignment() {
    isCurrent = false;
    endedAt = Instant.now();
  }
}
