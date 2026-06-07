package com.camelbytes.sitrep.users.internal;

import com.camelbytes.sitrep.users.api.UserDto;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Validated
public class UserController {
  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @GetMapping("/{id}")
  public UserDto getUserById(@PathVariable UUID id) {
    return service.getById(id);
  }

  @PostMapping
  public ResponseEntity<Void> createUser(@RequestBody @Valid UserCreateRequest request) {
    UUID userId = service.createUser(request);
    return ResponseEntity.created(URI.create("/api/v1/users/" + userId)).build();
  }
}
