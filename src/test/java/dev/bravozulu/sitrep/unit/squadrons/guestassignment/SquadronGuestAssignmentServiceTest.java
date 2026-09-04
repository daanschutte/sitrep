package dev.bravozulu.sitrep.unit.squadrons.guestassignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import dev.bravozulu.sitrep.shared.exceptions.NotFoundException;
import dev.bravozulu.sitrep.squadrons.api.SquadronGuestAssignmentDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronQueryService;
import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentService;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignment;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentCreateRequest;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentRepository;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SquadronGuestAssignmentServiceTest {
  @Mock SquadronRepository squadronRepository;
  @Mock UserRepository userRepository;
  @Mock SquadronGuestAssignmentRepository repository;
  @Mock SquadronAssignmentService squadronAssignmentService;

  SquadronQueryService squadronQueryService;
  UserQueryService userQueryService;
  SquadronGuestAssignmentService service;

  @BeforeEach
  void setUp() {
    squadronQueryService = new SquadronService(squadronRepository);
    userQueryService = new UserService(userRepository);
    service =
        new SquadronGuestAssignmentService(
            repository, squadronAssignmentService, squadronQueryService, userQueryService);
  }

  @Nested
  class GetSquadronGuestAssignmentsByUserId {
    @Test
    void getSquadronGuestAssignmentsByUserId_mapsToDtos() {
      UUID userId = UUID.randomUUID();
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentDto assignment =
          new SquadronGuestAssignmentDto(squadronId, userId, SquadronRole.OPS);

      when(repository.findAllByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of(assignment));

      List<SquadronGuestAssignmentDto> result = service.getSquadronGuestAssignmentsByUserId(userId);

      assertThat(result)
          .containsExactly(new SquadronGuestAssignmentDto(squadronId, userId, SquadronRole.OPS));
    }

    @Test
    void getSquadronGuestAssignmentsByUserId_none_returnsEmptyList() {
      UUID userId = UUID.randomUUID();
      when(repository.findAllByUserIdAndRevokedAtIsNull(userId)).thenReturn(List.of());

      assertThat(service.getSquadronGuestAssignmentsByUserId(userId)).isEmpty();
    }
  }

  @Nested
  class GetSquadronGuestAssignmentsBySquadronId {
    @Test
    void getSquadronGuestAssignmentsBySquadronId_delegatesToRepository() {
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentDto dto =
          new SquadronGuestAssignmentDto(squadronId, UUID.randomUUID(), SquadronRole.OPS);
      when(repository.findAllBySquadronIdAndRevokedAtIsNull(squadronId)).thenReturn(List.of(dto));

      assertThat(service.getSquadronGuestAssignmentsBySquadronId(squadronId)).containsExactly(dto);
    }

    @Test
    void getSquadronGuestAssignmentsBySquadronId_none_returnsEmptyList() {
      UUID squadronId = UUID.randomUUID();
      when(repository.findAllBySquadronIdAndRevokedAtIsNull(squadronId)).thenReturn(List.of());

      assertThat(service.getSquadronGuestAssignmentsBySquadronId(squadronId)).isEmpty();
    }
  }

  @Nested
  class AssignGuestSquadron {
    @Test
    void assignGuestSquadron_squadronNotFound_throwsException() {
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentCreateRequest request =
          new SquadronGuestAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.OPS);

      when(squadronRepository.existsById(squadronId)).thenReturn(false);

      assertThatThrownBy(() -> service.assignGuestSquadron(squadronId, request))
          .isExactlyInstanceOf(SquadronNotFoundException.class)
          .hasMessageContaining(squadronId.toString());
    }

    @Test
    void assignGuestSquadron_userNotFound_throwsException() {
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentCreateRequest request =
          new SquadronGuestAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.OPS);

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(false);

      assertThatThrownBy(() -> service.assignGuestSquadron(squadronId, request))
          .isExactlyInstanceOf(UserNotFoundException.class)
          .hasMessageContaining(request.userId().toString());
    }

    @Test
    void assignGuestSquadron_targetIsPrimarySquadron_throwsConflictException() {
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentCreateRequest request =
          new SquadronGuestAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.OPS);

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(squadronAssignmentService.isUserPrimarySquadron(squadronId, request.userId()))
          .thenReturn(true);

      assertThatThrownBy(() -> service.assignGuestSquadron(squadronId, request))
          .isInstanceOf(ConflictException.class);

      verify(repository, never()).save(any());
    }

    @Test
    void assignGuestSquadron_noExisting_persistsCorrectUserAndSquadronIds() {
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentCreateRequest request =
          new SquadronGuestAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.OPS);

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(squadronAssignmentService.isUserPrimarySquadron(squadronId, request.userId()))
          .thenReturn(false);
      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, request.userId()))
          .thenReturn(Optional.empty());

      ArgumentCaptor<SquadronGuestAssignment> captor =
          ArgumentCaptor.forClass(SquadronGuestAssignment.class);
      when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

      service.assignGuestSquadron(squadronId, request);

      SquadronGuestAssignment saved = captor.getValue();
      assertThat(saved.getUserId()).isEqualTo(request.userId());
      assertThat(saved.getSquadronId()).isEqualTo(squadronId);
      assertThat(saved.getRole()).isEqualTo(request.role());
    }

    @Test
    void assignGuestSquadron_existingActiveAssignment_throwsConflictException() {
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentCreateRequest request =
          new SquadronGuestAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.OPS);
      SquadronGuestAssignment existing =
          new SquadronGuestAssignment(squadronId, request.userId(), SquadronRole.STUDENT);

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(squadronAssignmentService.isUserPrimarySquadron(squadronId, request.userId()))
          .thenReturn(false);
      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, request.userId()))
          .thenReturn(Optional.of(existing));

      assertThatThrownBy(() -> service.assignGuestSquadron(squadronId, request))
          .isInstanceOf(ConflictException.class);
      verify(repository, never()).save(any());
    }

    @Test
    void assignGuestSquadron_duplicate_throwsConflictException() {
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentCreateRequest request =
          new SquadronGuestAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.OPS);

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(squadronAssignmentService.isUserPrimarySquadron(squadronId, request.userId()))
          .thenReturn(false);
      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, request.userId()))
          .thenReturn(Optional.empty());
      when(repository.save(any())).thenThrow(new DataIntegrityViolationException(""));

      assertThatThrownBy(() -> service.assignGuestSquadron(squadronId, request))
          .isInstanceOf(ConflictException.class);
    }
  }

  @Nested
  class ChangeGuestSquadronRole {
    @Test
    void changeGuestSquadronRole_differentRole_updatesRole() {
      UUID squadronId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      SquadronGuestAssignment assignment =
          new SquadronGuestAssignment(squadronId, userId, SquadronRole.STUDENT);

      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId))
          .thenReturn(Optional.of(assignment));

      service.changeGuestSquadronRole(squadronId, userId, SquadronRole.OPS);

      assertThat(assignment.getRole()).isEqualTo(SquadronRole.OPS);
      verify(repository).saveAndFlush(assignment);
    }

    @Test
    void changeGuestSquadronRole_sameRole_doesNothing() {
      UUID squadronId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      SquadronGuestAssignment assignment =
          new SquadronGuestAssignment(squadronId, userId, SquadronRole.OPS);

      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId))
          .thenReturn(Optional.of(assignment));

      service.changeGuestSquadronRole(squadronId, userId, SquadronRole.OPS);

      verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void changeGuestSquadronRole_notFound_throwsNotFoundException() {
      UUID squadronId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId))
          .thenReturn(Optional.empty());

      assertThatThrownBy(
              () -> service.changeGuestSquadronRole(squadronId, userId, SquadronRole.OPS))
          .isInstanceOf(NotFoundException.class);
    }
  }

  @Nested
  class RevokeSquadronGuestAssignment {
    @Test
    void revokeSquadronGuestAssignment_found_setsRevokedAt() {
      UUID squadronId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      SquadronGuestAssignment assignment =
          new SquadronGuestAssignment(squadronId, userId, SquadronRole.OPS);

      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId))
          .thenReturn(Optional.of(assignment));

      service.revokeSquadronGuestAssignment(squadronId, userId);

      assertThat(assignment.isActive()).isFalse();
      verify(repository).saveAndFlush(assignment);
    }

    @Test
    void revokeSquadronGuestAssignment_notFound_throwsNotFoundException() {
      UUID squadronId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.revokeSquadronGuestAssignment(squadronId, userId))
          .isInstanceOf(NotFoundException.class);
    }
  }
}
