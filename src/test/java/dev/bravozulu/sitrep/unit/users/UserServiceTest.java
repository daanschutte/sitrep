package dev.bravozulu.sitrep.unit.users;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import dev.bravozulu.sitrep.users.api.UserDto;
import dev.bravozulu.sitrep.users.internal.User;
import dev.bravozulu.sitrep.users.internal.UserCreateRequest;
import dev.bravozulu.sitrep.users.internal.UserNotActiveException;
import dev.bravozulu.sitrep.users.internal.UserNotFoundException;
import dev.bravozulu.sitrep.users.internal.UserRepository;
import dev.bravozulu.sitrep.users.internal.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
  @Mock UserRepository repository;
  @InjectMocks UserService service;

  @Nested
  class GetById {
    @Test
    void getById_returnsDto() {
      User user = new User("Chuck", "Yeager", "sonic@boom.com", "Gen");
      UUID userId = UUID.randomUUID();
      ReflectionTestUtils.setField(user, "id", userId);

      when(repository.findById(userId)).thenReturn(Optional.of(user));

      UserDto expected =
          new UserDto(
              userId,
              user.getFirstName(),
              user.getLastName(),
              user.getEmail(),
              user.getRank(),
              true);
      UserDto actual = service.getById(userId);

      assertThat(actual).isEqualTo(expected);
    }

    @Test
    void getById_notFound_throwsUserNotFoundException() {
      when(repository.findById(any())).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.getById(UUID.randomUUID()))
          .isInstanceOf(UserNotFoundException.class);
    }
  }

  @Nested
  class CreateUser {
    @Test
    void createUser_duplicateEmail_throwsConflictException() {
      when(repository.save(any())).thenThrow(new DataIntegrityViolationException(""));
      UserCreateRequest request = new UserCreateRequest("Chuck", "Yeager", "sonic@boom.com", "Gen");
      assertThatThrownBy(() -> service.createUser(request))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining("User with email sonic@boom.com already exists");
    }
  }

  @Nested
  class ValidateActiveUserExists {
    @Test
    void validateActiveUserExists_activeUser_doesNotThrow() {
      UUID userId = UUID.randomUUID();
      when(repository.findById(userId))
          .thenReturn(Optional.of(new User("Chuck", "Yeager", "sonic@boom.com", "Gen")));

      service.validateActiveUserExists(userId);
    }

    @Test
    void validateActiveUserExists_notFound_throwsUserNotFoundException() {
      UUID userId = UUID.randomUUID();
      when(repository.findById(userId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.validateActiveUserExists(userId))
          .isExactlyInstanceOf(UserNotFoundException.class)
          .hasMessageContaining(userId.toString());
    }

    @Test
    void validateActiveUserExists_deactivatedUser_throwsUserNotActiveException() {
      UUID userId = UUID.randomUUID();
      User user = new User("Chuck", "Yeager", "sonic@boom.com", "Gen");
      user.deactivate();
      when(repository.findById(userId)).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> service.validateActiveUserExists(userId))
          .isExactlyInstanceOf(UserNotActiveException.class)
          .hasMessageContaining(userId.toString());
    }
  }

  @Nested
  class DeactivateUser {
    @Test
    void deactivateUser_activeUser_deactivatesAndSaves() {
      UUID userId = UUID.randomUUID();
      User user = new User("Chuck", "Yeager", "sonic@boom.com", "Gen");
      when(repository.findById(userId)).thenReturn(Optional.of(user));

      service.deactivateUser(userId);

      assertThat(user.isActive()).isFalse();
      verify(repository).save(user);
    }

    @Test
    void deactivateUser_notFound_throwsUserNotFoundException() {
      UUID userId = UUID.randomUUID();
      when(repository.findById(userId)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.deactivateUser(userId))
          .isExactlyInstanceOf(UserNotFoundException.class);
      verify(repository, never()).save(any());
    }

    @Test
    void deactivateUser_alreadyDeactivated_throwsUserNotActiveException() {
      UUID userId = UUID.randomUUID();
      User user = new User("Chuck", "Yeager", "sonic@boom.com", "Gen");
      user.deactivate();
      when(repository.findById(userId)).thenReturn(Optional.of(user));

      assertThatThrownBy(() -> service.deactivateUser(userId))
          .isExactlyInstanceOf(UserNotActiveException.class);
      verify(repository, never()).save(any());
    }
  }
}
