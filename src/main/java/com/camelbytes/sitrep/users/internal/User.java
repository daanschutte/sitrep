package com.camelbytes.sitrep.users.internal;

import com.camelbytes.sitrep.shared.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(name = "users")
public class User extends BaseEntity {
  @NotBlank private String firstName;
  @NotBlank private String lastName;
  @Email private String email;
  @NotBlank private String rank;

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
