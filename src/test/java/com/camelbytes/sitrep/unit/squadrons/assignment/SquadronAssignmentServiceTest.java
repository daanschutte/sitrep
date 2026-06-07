package com.camelbytes.sitrep.unit.squadrons.assignment;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.camelbytes.sitrep.shared.exceptions.ConflictException;
import com.camelbytes.sitrep.squadrons.api.SquadronAssignmentDto;
import com.camelbytes.sitrep.squadrons.api.SquadronRole;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignment;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignmentCreateRequest;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignmentNotFoundException;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignmentRepository;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignmentService;
import com.camelbytes.sitrep.squadrons.internal.squadron.SquadronService;
import com.camelbytes.sitrep.users.api.UserQueryService;
import java.util.Optional;
import java.util.Set;
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
class SquadronAssignmentServiceTest {
  @Mock SquadronService squadronService;

  @Mock SquadronAssignmentRepository repository;
  @Mock UserQueryService userQueryService;
  @InjectMocks SquadronAssignmentService service;

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

      doNothing().when(squadronService).validateSquadronExists(squadronId);
      doNothing().when(userQueryService).validateUserExists(request.userId());
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
      doNothing().when(squadronService).validateSquadronExists(squadronId);
      doNothing().when(userQueryService).validateUserExists(request.userId());
      when(repository.findByUserIdAndEndedAtIsNull(request.userId())).thenReturn(Optional.empty());
      when(repository.save(any())).thenThrow(new DataIntegrityViolationException(""));

      assertThatThrownBy(() -> service.createSquadronAssignment(squadronId, request))
          .isInstanceOf(ConflictException.class);
    }
  }
}
