package dev.bravozulu.sitrep.auth.internal.credential;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Validated
public class CredentialController {
  private final ActivationTokenService activationTokenService;
  private final CredentialService credentialService;

  public CredentialController(
      ActivationTokenService activationTokenService, CredentialService credentialService) {
    this.activationTokenService = activationTokenService;
    this.credentialService = credentialService;
  }

  // TODO(AUTH-2): ADMIN-only once SecurityConfig stops permitting all requests.
  @PostMapping("/activation-tokens")
  public ResponseEntity<IssuedActivationToken> issueActivationToken(
      @RequestBody @Valid ActivationTokenCreateRequest request) {
    IssuedActivationToken issued = activationTokenService.issue(request.userId(), request.role());
    return ResponseEntity.status(HttpStatus.CREATED).body(issued);
  }

  @PostMapping("/credentials")
  public ResponseEntity<Void> createCredential(
      @RequestBody @Valid CredentialCreateRequest request) {
    credentialService.redeem(request.token(), request.password());
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }
}
