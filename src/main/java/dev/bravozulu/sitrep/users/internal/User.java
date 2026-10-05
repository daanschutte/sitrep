package dev.bravozulu.sitrep.users.internal;

import dev.bravozulu.sitrep.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Locale;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
  private String firstName;
  private String lastName;
  private String email;
  private String rank;
  private boolean isActive;

  protected User() {}

  public User(String firstName, String lastName, String email, String rank) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = normaliseEmail(email);
    this.rank = rank;
    this.isActive = true;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public String getEmail() {
    return email;
  }

  public String getRank() {
    return rank;
  }

  public boolean isActive() {
    return isActive;
  }

  public void deactivate() {
    this.isActive = false;
  }

  static String normaliseEmail(String email) {
    return email.strip().toLowerCase(Locale.ROOT);
  }
}
