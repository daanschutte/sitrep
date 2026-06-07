package com.camelbytes.sitrep.squadrons.internal.squadron;

import com.camelbytes.sitrep.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Optional;

@Entity
@Table(name = "squadron")
public class Squadron extends BaseEntity {
  @Column(nullable = false)
  private String name;

  @Column(unique = true)
  private String shortName;

  @Column(nullable = false)
  private boolean isActive;

  protected Squadron() {}

  public Squadron(String name) {
    this(name, null);
  }

  public Squadron(String name, String shortName) {
    this.name = name;
    this.shortName = shortName;
    this.isActive = true;
  }

  public String getName() {
    return name;
  }

  public Optional<String> getShortName() {
    return Optional.ofNullable(shortName);
  }

  public boolean isActive() {
    return isActive;
  }

  public void enable() {
    this.isActive = true;
  }

  public void disable() {
    this.isActive = false;
  }
}
