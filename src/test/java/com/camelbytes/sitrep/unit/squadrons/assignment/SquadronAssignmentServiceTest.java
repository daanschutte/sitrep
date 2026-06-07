package com.camelbytes.sitrep.unit.squadrons.assignment;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.camelbytes.sitrep.shared.exceptions.ConflictException;
import com.camelbytes.sitrep.squadrons.api.SquadronAssignmentDto;
import com.camelbytes.sitrep.squadrons.api.SquadronQueryService;
import com.camelbytes.sitrep.squadrons.api.SquadronRole;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignment;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignmentCreateRequest;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignmentNotFoundException;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignmentRepository;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignmentService;
import com.camelbytes.sitrep.squadrons.internal.squadron.SquadronNotFoundException;
import com.camelbytes.sitrep.squadrons.internal.squadron.SquadronRepository;
import com.camelbytes.sitrep.squadrons.internal.squadron.SquadronService;
import com.camelbytes.sitrep.users.api.UserQueryService;
import com.camelbytes.sitrep.users.internal.UserNotFoundException;
import com.camelbytes.sitrep.users.internal.UserRepository;
import com.camelbytes.sitrep.users.internal.UserService;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
  class GetCurrentSquadronAssignmentsBySquadronId {
    @Test
    void getBySquadronId_returnsDtoList() {
      UUID squadronId = UUID.randomUUID();
      UUID userId1 = UUID.randomUUID();
      UUID userId2 = UUID.randomUUID();

      SquadronAssignment assignment1 =
          new SquadronAssignment(userId1, squadronId, SquadronRole.INSTRUCTOR);
      SquadronAssignment assignment2 =
          new SquadronAssignment(userId2, squadronId, SquadronRole.STUDENT);

      when(repository.findBySquadronIdAndEndedAtIsNull(squadronId))
          .thenReturn(List.of(assignment1, assignment2));

      List<SquadronAssignmentDto> result =
          service.getCurrentSquadronAssignmentsBySquadronId(squadronId);

      assertThat(result.size()).isEqualTo(2);
      assertThat(result.get(0).userId()).isEqualTo(userId1);
      assertThat(result.get(1).userId()).isEqualTo(userId2);
    }

    @Test
    void getBySquadronId_noneFound_returnsEmptyList() {
      UUID squadronId = UUID.randomUUID();
      when(repository.findBySquadronIdAndEndedAtIsNull(squadronId)).thenReturn(List.of());

      List<SquadronAssignmentDto> result =
          service.getCurrentSquadronAssignmentsBySquadronId(squadronId);

      assertThat(result.isEmpty()).isTrue();
    }
  }

  @Nested
  class GetSquadronAssignmentByUserId {
    @Test
    void getByUserId_returnsDto() {
      UUID id = UUID.randomUUID();
      UUID userId = UUID.randomUUID();
      UUID squadronId = UUID.randomUUID();

      SquadronAssignment assignment =
          new SquadronAssignment(userId, squadronId, SquadronRole.INSTRUCTOR);
      ReflectionTestUtils.setField(assignment, "id", id);

      when(repository.findByUserIdAndEndedAtIsNull(userId)).thenReturn(Optional.of(assignment));

      SquadronAssignmentDto result = service.getSquadronAssignmentByUserId(userId);

      assertThat(result.id()).isEqualTo(id);
      assertThat(result.userId()).isEqualTo(userId);
      assertThat(result.primarySquadronId()).isEqualTo(squadronId);
      assertThat(result.role()).isEqualTo(SquadronRole.INSTRUCTOR);
      assertThat(result.guestSquadronIds()).isEqualTo(Set.of());
    }

    @Test
    void getByUserId_notFound_throwsNotFoundException() {
      when(repository.findByUserIdAndEndedAtIsNull(any())).thenReturn(Optional.empty());
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
          new SquadronAssignment(request.userId(), squadronId, request.role());
      ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(repository.findByUserIdAndEndedAtIsNull(request.userId())).thenReturn(Optional.empty());
      when(repository.save(any())).thenReturn(saved);

      service.createSquadronAssignment(squadronId, request);

      verify(repository).save(any(SquadronAssignment.class));
      verify(repository).findByUserIdAndEndedAtIsNull(request.userId());
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
          new SquadronAssignment(request.userId(), UUID.randomUUID(), SquadronRole.INSTRUCTOR);
      ReflectionTestUtils.setField(existing, "id", UUID.randomUUID());

      SquadronAssignment saved =
          new SquadronAssignment(request.userId(), squadronId, request.role());
      ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());

      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(repository.findByUserIdAndEndedAtIsNull(request.userId()))
          .thenReturn(Optional.of(existing));
      when(repository.save(any())).thenReturn(saved);

      service.createSquadronAssignment(squadronId, request);

      assertThat(existing.getEndedAt()).isNotEmpty();
      verify(repository).saveAndFlush(existing);
    }

    @Test
    void createSquadronAssignment_duplicate_throwsConflictException() {
      UUID squadronId = UUID.randomUUID();
      SquadronAssignmentCreateRequest request =
          new SquadronAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.STUDENT);
      when(squadronRepository.existsById(squadronId)).thenReturn(true);
      when(userRepository.existsById(request.userId())).thenReturn(true);
      when(repository.findByUserIdAndEndedAtIsNull(request.userId())).thenReturn(Optional.empty());
      when(repository.save(any())).thenThrow(new DataIntegrityViolationException(""));

      assertThatThrownBy(() -> service.createSquadronAssignment(squadronId, request))
          .isInstanceOf(ConflictException.class);
    }
  }
}
