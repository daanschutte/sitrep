package com.camelbytes.sitrep.users.internal;

import com.camelbytes.sitrep.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
  private String firstName;
  private String lastName;
  private String email;
  private String rank;

  protected User() {}

  public User(String firstName, String lastName, String email, String rank) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
    this.rank = rank;
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
}
