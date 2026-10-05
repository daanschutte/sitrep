package dev.bravozulu.sitrep.auth.internal.credential;

import dev.bravozulu.sitrep.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "credential")
public class Credential extends BaseEntity {
  @Column(nullable = false, updatable = false)
  private UUID userId;

  @Column(nullable = false)
  private String passwordHash;

  @Column(nullable = false)
  @Enumerated(EnumType.STRING)
  private SystemRole role;

  protected Credential() {}

  public Credential(UUID userId, String passwordHash, SystemRole role) {
    this.userId = userId;
    this.passwordHash = passwordHash;
    this.role = role;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public SystemRole getRole() {
    return role;
  }
}
