package dev.bravozulu.sitrep.users.internal;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import dev.bravozulu.sitrep.users.api.UserDto;
import dev.bravozulu.sitrep.users.api.UserQueryService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
public class UserService implements UserQueryService {
  private static final Logger log = LoggerFactory.getLogger(UserService.class);

  private final UserRepository repository;

  public UserService(UserRepository repository) {
    this.repository = repository;
  }

  @Override
  public void validateActiveUserExists(UUID userId) {
    User user = repository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    if (!user.isActive()) {
      throw new UserNotActiveException("User with userId=" + userId + " is already deactivated");
    }
  }

  public UserDto getById(UUID id) {
    return repository
        .findById(id)
        .map(UserService::toDto)
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

  public void deactivateUser(UUID userId) {
    User user = repository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    if (!user.isActive()) {
      throw new UserNotActiveException("User with userId=" + userId + " is already deactivated");
    }

    user.deactivate();
    repository.save(user);
    log.debug("User with userId={} deactivated", userId);
  }

  private static UserDto toDto(User user) {
    return new UserDto(
        user.getId(),
        user.getFirstName(),
        user.getLastName(),
        user.getEmail(),
        user.getRank(),
        user.isActive());
  }
}
