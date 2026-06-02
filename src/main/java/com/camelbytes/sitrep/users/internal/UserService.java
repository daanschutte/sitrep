package com.camelbytes.sitrep.users.internal;

import com.camelbytes.sitrep.shared.exceptions.ConflictException;
import com.camelbytes.sitrep.users.api.UserDto;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class UserService {
  private final UserRepository repository;

  public UserService(UserRepository repository) {
    this.repository = repository;
  }

  public UserDto getById(UUID id) {
    return repository
        .findById(id)
        .map(UserService::fromUser)
        .orElseThrow(() -> new UserNotFoundException(id));
  }

  public UUID createUser(UserCreateRequest request) {
    User user = new User(request.firstName(), request.lastName(), request.email(), request.rank());
    try {
      user = repository.save(user);
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException("User with email " + request.email() + " already exists");
    }
    return user.getId();
  }

  private static UserDto fromUser(User user) {
    return new UserDto(
        user.getId(), user.getFirstName(), user.getLastName(), user.getEmail(), user.getRank());
  }
}
