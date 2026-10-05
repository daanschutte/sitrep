package dev.bravozulu.sitrep.auth.internal.credential;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CredentialCreateRequest(
    @NotBlank String token, @NotNull @Size(min = 12) @MaxUtf8Bytes(72) String password) {
  @Override
  public String toString() {
    return "CredentialCreateRequest[token=<redacted>, password=<redacted>]";
  }
}
