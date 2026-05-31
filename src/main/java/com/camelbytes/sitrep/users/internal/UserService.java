package com.camelbytes.sitrep.users.internal;

import com.camelbytes.sitrep.users.api.UserDto;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final UserRepository repository;

  public UserService(UserRepository repository) {
    this.repository = repository;
  }

  public UserDto findById(UUID id) {
    return repository
        .findById(id)
        .map(UserService::fromUser)
        .orElseThrow(() -> new UserNotFoundException(id.toString()));
  }

  public UUID createUser(UserCreateRequest request) {
    User user = new User(request.firstName(), request.lastName(), request.email(), request.rank());
    user = repository.save(user);
    return user.getId();
  }

  private static UserDto fromUser(User user) {
    return new UserDto(
        user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getRank());
  }
}
