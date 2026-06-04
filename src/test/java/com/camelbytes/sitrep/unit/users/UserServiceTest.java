package com.camelbytes.sitrep.unit.users;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.camelbytes.sitrep.shared.exceptions.ConflictException;
import com.camelbytes.sitrep.users.api.UserDto;
import com.camelbytes.sitrep.users.internal.User;
import com.camelbytes.sitrep.users.internal.UserCreateRequest;
import com.camelbytes.sitrep.users.internal.UserNotFoundException;
import com.camelbytes.sitrep.users.internal.UserRepository;
import com.camelbytes.sitrep.users.internal.UserService;
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
              userId, user.getFirstName(), user.getLastName(), user.getEmail(), user.getRank());
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
}
