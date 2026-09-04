package dev.bravozulu.sitrep.unit.squadrons.assignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import dev.bravozulu.sitrep.squadrons.api.SquadronAssignmentDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronQueryService;
import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignment;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentCreateRequest;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentNotFoundException;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentRepository;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentService;
import dev.bravozulu.sitrep.squadrons.internal.squadron.SquadronNotFoundException;
import dev.bravozulu.sitrep.squadrons.internal.squadron.SquadronRepository;
import dev.bravozulu.sitrep.squadrons.internal.squadron.SquadronService;
import dev.bravozulu.sitrep.users.api.UserQueryService;
import dev.bravozulu.sitrep.users.internal.UserNotFoundException;
import dev.bravozulu.sitrep.users.internal.UserRepository;
import dev.bravozulu.sitrep.users.internal.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SquadronAssignmentServiceTest {
  @Mock SquadronRepository squadronRepository;
  @Mock UserRepository userRepository;
  @Mock SquadronAssignmentRepository repository;

  SquadronQueryService squadronQueryService;
  UserQueryService userQueryService;
  SquadronAssignmentService service;

  @BeforeEach
  void setUp() {
    squadronQueryService = new SquadronService(squadronRepository);
    userQueryService = new UserService(userRepository);
    service = new SquadronAssignmentService(repository, squadronQueryService, userQueryService);
  }

  @Nested
  class IsUserPrimarySquadron {
    @Test
    void isUserPrimarySquadron_matchesCurrentAssignment_returnsTrue() {
      UUID userId = UUID.randomUUID();
      UUID squadronId = UUID.fromString("11111111-1111-1111-1111-111111111111");
      UUID sameValueDifferentInstance = UUID.fromString(squadronId.toString());

      SquadronAssignment assignment =
          new SquadronAssignment(sameValueDifferentInstance, userId, SquadronRole.STUDENT);
      when(repository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(Optional.of(assignment));

      assertThat(service.isUserPrimarySquadron(squadronId, userId)).isTrue();
    }

    @Test
    void isUserPrimarySquadron_differentSquadron_returnsFalse() {
      UUID userId = UUID.randomUUID();
      SquadronAssignment assignment =
          new SquadronAssignment(UUID.randomUUID(), userId, SquadronRole.STUDENT);
      when(repository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(Optional.of(assignment));

      assertThat(service.isUserPrimarySquadron(UUID.randomUUID(), userId)).isFalse();
    }

    @Test
    void isUserPrimarySquadron_noAssignment_returnsFalse() {
      UUID userId = UUID.randomUUID();
      when(repository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(Optional.empty());

      assertThat(service.isUserPrimarySquadron(UUID.randomUUID(), userId)).isFalse();
    }
  }

  @Nested
  class GetSquadronAssignmentsBySquadronId {
    @Test
    void getBySquadronId_returnsAssignments() {
      UUID squadronId = UUID.randomUUID();
      SquadronAssignment assignment1 =
          new SquadronAssignment(squadronId, UUID.randomUUID(), SquadronRole.INSTRUCTOR);
      ReflectionTestUtils.setField(assignment1, "id", UUID.randomUUID());
      SquadronAssignment assignment2 =
          new SquadronAssignment(squadronId, UUID.randomUUID(), SquadronRole.STUDENT);

      ReflectionTestUtils.setField(assignment2, "id", UUID.randomUUID());
      when(repository.findBySquadronIdAndRevokedAtIsNull(squadronId))
          .thenReturn(List.of(assignment1, assignment2));

      SquadronAssignmentDto assignment1Dto =
          new SquadronAssignmentDto(
                  assignment1.getSquadronId(), assignment1.getUserId(), assignment1.getRole());
      ReflectionTestUtils.setField(assignment1, "id", UUID.randomUUID());
      SquadronAssignmentDto assignment2Dto =
          new SquadronAssignmentDto(
                  assignment2.getSquadronId(), assignment2.getUserId(), assignment2.getRole());
      ReflectionTestUtils.setField(assignment2, "id", UUID.randomUUID());

      List<SquadronAssignmentDto> result = service.getSquadronAssignmentsBySquadronId(squadronId);

      assertThat(result).containsExactly(assignment1Dto, assignment2Dto);
    }

    @Test
    void getBySquadronId_noneFound_returnsEmptyList() {
      UUID squadronId = UUID.randomUUID();
      when(repository.findBySquadronIdAndRevokedAtIsNull(squadronId)).thenReturn(List.of());

      assertThat(service.getSquadronAssignmentsBySquadronId(squadronId)).isEmpty();
    }
  }

  @Nested
  class GetSquadronAssignmentByUserId {
    @Test
    void getByUserId_returnsAssignment() {
      UUID userId = UUID.randomUUID();
      UUID squadronId = UUID.randomUUID();
      SquadronAssignment assignment =
          new SquadronAssignment(squadronId, userId, SquadronRole.INSTRUCTOR);

      when(repository.findByUserIdAndRevokedAtIsNull(userId)).thenReturn(Optional.of(assignment));

      SquadronAssignmentDto result = service.getSquadronAssignmentByUserId(userId);

      assertThat(result.userId()).isEqualTo(userId);
      assertThat(result.squadronId()).isEqualTo(squadronId);
      assertThat(result.role()).isEqualTo(SquadronRole.INSTRUCTOR);
    }

    @Test
    void getByUserId_notFound_throwsNotFoundException() {
      when(repository.findByUserIdAndRevokedAtIsNull(any())).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getSquadronAssignmentByUserId(UUID.randomUUID()))
          .isInstanceOf(SquadronAssignmentNotFoundException.class);
    }
  }

  @Nested
  class CreateSquadronAssignment {
    @Test
    void createSquadronAssignment_noExistingAssignment_createsNew() {
      UUID squadronId = UUID.randomUUID();
      SquadronAssignmentCreateRequest request =
          new SquadronAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.STUDENT);

      SquadronAssignment saved =
          new SquadronAssignment(squadronId, request.userId(), request.role());
      ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(repository.findByUserIdAndRevokedAtIsNull(request.userId()))
          .thenReturn(Optional.empty());
      when(repository.save(any())).thenReturn(saved);

      service.createSquadronAssignment(squadronId, request);

      verify(repository).save(any(SquadronAssignment.class));
      verify(repository).findByUserIdAndRevokedAtIsNull(request.userId());
    }

    @Test
    void createSquadronAssignment_squadronNotFound_throwsException() {
      UUID squadronId = UUID.randomUUID();
      SquadronAssignmentCreateRequest request =
          new SquadronAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.STUDENT);

      when(squadronRepository.existsById(squadronId)).thenReturn(false);

      assertThatThrownBy(() -> service.createSquadronAssignment(squadronId, request))
          .isExactlyInstanceOf(SquadronNotFoundException.class)
          .hasMessageContaining(squadronId.toString());
    }

    @Test
    void createSquadronAssignment_userNotFound_throwsException() {
      UUID squadronId = UUID.randomUUID();
      SquadronAssignmentCreateRequest request =
          new SquadronAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.STUDENT);

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(false);

      assertThatThrownBy(() -> service.createSquadronAssignment(squadronId, request))
          .isExactlyInstanceOf(UserNotFoundException.class)
          .hasMessageContaining(request.userId().toString());
    }

    @Test
    void createSquadronAssignment_existingAssignment_endsExistingAndCreatesNew() {
      UUID squadronId = UUID.randomUUID();
      SquadronAssignmentCreateRequest request =
          new SquadronAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.STUDENT);

      SquadronAssignment existing =
          new SquadronAssignment(UUID.randomUUID(), request.userId(), SquadronRole.INSTRUCTOR);
      ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

      SquadronAssignment saved =
          new SquadronAssignment(squadronId, request.userId(), request.role());
      ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(repository.findByUserIdAndRevokedAtIsNull(request.userId()))
          .thenReturn(Optional.of(existing));
      when(repository.save(any())).thenReturn(saved);

      service.createSquadronAssignment(squadronId, request);

      assertThat(existing.getRevokedAt()).isPresent();
      verify(repository).saveAndFlush(existing);
    }

    @Test
    void createSquadronAssignment_duplicate_throwsConflictException() {
      UUID squadronId = UUID.randomUUID();
      SquadronAssignmentCreateRequest request =
          new SquadronAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.STUDENT);
      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(repository.findByUserIdAndRevokedAtIsNull(request.userId()))
          .thenReturn(Optional.empty());
      when(repository.save(any())).thenThrow(new DataIntegrityViolationException(""));

      assertThatThrownBy(() -> service.createSquadronAssignment(squadronId, request))
          .isInstanceOf(ConflictException.class);
    }
  }

  @Nested
  class RevokeSquadronAssignment {
    @Test
    void revokeSquadronAssignment_found_setsRevokedAt() {
      UUID squadronId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      SquadronAssignment assignment =
          new SquadronAssignment(squadronId, userId, SquadronRole.STUDENT);

      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId))
          .thenReturn(Optional.of(assignment));

      service.revokeSquadronAssignment(squadronId, userId);

      assertThat(assignment.getRevokedAt()).isPresent();
    }

    @Test
    void revokeSquadronAssignment_notFound_throwsNotFoundException() {
      UUID squadronId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.revokeSquadronAssignment(squadronId, userId))
          .isInstanceOf(SquadronAssignmentNotFoundException.class);
    }
  }
}
