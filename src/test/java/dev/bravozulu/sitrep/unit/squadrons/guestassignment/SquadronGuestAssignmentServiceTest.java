package dev.bravozulu.sitrep.unit.squadrons.guestassignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.bravozulu.sitrep.shared.exceptions.ConflictException;
import dev.bravozulu.sitrep.squadrons.api.SquadronGuestAssignmentDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronQueryService;
import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentService;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignment;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentCreateRequest;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentNotFoundException;
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
import org.springframework.test.util.ReflectionTestUtils;

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
  class FindSquadronGuestAssignment {
    @Test
    void findSquadronGuestAssignment_delegatesToRepository() {
      UUID squadronId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      SquadronGuestAssignment assignment =
          new SquadronGuestAssignment(squadronId, userId, SquadronRole.OPS);
      ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());
      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId))
          .thenReturn(Optional.of(assignment));

      assertThat(service.findSquadronGuestAssignment(squadronId, userId)).contains(assignment);
    }
  }

  @Nested
  class GetSquadronGuestAssignmentsByUserId {
    @Test
    void getSquadronGuestAssignmentsByUserId_mapsToDtos() {
      UUID userId = UUID.randomUUID();
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignment assignment =
          new SquadronGuestAssignment(squadronId, userId, SquadronRole.OPS);
      ReflectionTestUtils.setField(assignment, "id", UUID.randomUUID());

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
      when(repository.findBySquadronIdAndRevokedAtIsNull(squadronId)).thenReturn(List.of(dto));

      assertThat(service.getSquadronGuestAssignmentsBySquadronId(squadronId)).containsExactly(dto);
    }

    @Test
    void getSquadronGuestAssignmentsBySquadronId_none_returnsEmptyList() {
      UUID squadronId = UUID.randomUUID();
      when(repository.findBySquadronIdAndRevokedAtIsNull(squadronId)).thenReturn(List.of());

      assertThat(service.getSquadronGuestAssignmentsBySquadronId(squadronId)).isEmpty();
    }
  }

  @Nested
  class CreateSquadronGuestAssignment {
    @Test
    void createSquadronGuestAssignment_squadronNotFound_throwsException() {
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentCreateRequest request =
          new SquadronGuestAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.OPS);

      when(squadronRepository.existsById(squadronId)).thenReturn(false);

      assertThatThrownBy(() -> service.createSquadronGuestAssignment(squadronId, request))
          .isExactlyInstanceOf(SquadronNotFoundException.class)
          .hasMessageContaining(squadronId.toString());
    }

    @Test
    void createSquadronGuestAssignment_userNotFound_throwsException() {
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentCreateRequest request =
          new SquadronGuestAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.OPS);

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(false);

      assertThatThrownBy(() -> service.createSquadronGuestAssignment(squadronId, request))
          .isExactlyInstanceOf(UserNotFoundException.class)
          .hasMessageContaining(request.userId().toString());
    }

    @Test
    void createSquadronGuestAssignment_targetIsPrimarySquadron_throwsConflictException() {
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentCreateRequest request =
          new SquadronGuestAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.OPS);

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(squadronAssignmentService.isUserPrimarySquadron(squadronId, request.userId()))
          .thenReturn(true);

      assertThatThrownBy(() -> service.createSquadronGuestAssignment(squadronId, request))
          .isInstanceOf(ConflictException.class);

      verify(repository, never()).save(any());
    }

    @Test
    void createSquadronGuestAssignment_noExisting_persistsCorrectUserAndSquadronIds() {
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

      service.createSquadronGuestAssignment(squadronId, request);

      SquadronGuestAssignment saved = captor.getValue();
      assertThat(saved.getUserId()).isEqualTo(request.userId());
      assertThat(saved.getSquadronId()).isEqualTo(squadronId);
      assertThat(saved.getRole()).isEqualTo(request.role());
    }

    @Test
    void createSquadronGuestAssignment_existingWithDifferentRole_updatesRole() {
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

      service.createSquadronGuestAssignment(squadronId, request);

      assertThat(existing.getRole()).isEqualTo(SquadronRole.OPS);
      verify(repository).saveAndFlush(existing);
      verify(repository, never()).save(any());
    }

    @Test
    void createSquadronGuestAssignment_existingWithSameRole_doesNothing() {
      UUID squadronId = UUID.randomUUID();
      SquadronGuestAssignmentCreateRequest request =
          new SquadronGuestAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.OPS);
      SquadronGuestAssignment existing =
          new SquadronGuestAssignment(squadronId, request.userId(), SquadronRole.OPS);

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(squadronAssignmentService.isUserPrimarySquadron(squadronId, request.userId()))
          .thenReturn(false);
      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, request.userId()))
          .thenReturn(Optional.of(existing));

      service.createSquadronGuestAssignment(squadronId, request);

      verify(repository, never()).saveAndFlush(any());
      verify(repository, never()).save(any());
    }

    @Test
    void createSquadronGuestAssignment_duplicate_throwsConflictException() {
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

      assertThatThrownBy(() -> service.createSquadronGuestAssignment(squadronId, request))
          .isInstanceOf(ConflictException.class);
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
    }

    @Test
    void revokeSquadronGuestAssignment_notFound_throwsNotFoundException() {
      UUID squadronId = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      when(repository.findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId))
          .thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.revokeSquadronGuestAssignment(squadronId, userId))
          .isInstanceOf(SquadronGuestAssignmentNotFoundException.class);
    }
  }
}
