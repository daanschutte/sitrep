package dev.bravozulu.sitrep.unit.squadrons.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.when;

import dev.bravozulu.sitrep.squadrons.api.SquadronAccessDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronAssignmentDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronGuestAssignmentDto;
import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import dev.bravozulu.sitrep.squadrons.api.UserSquadronAccessDto;
import dev.bravozulu.sitrep.squadrons.internal.access.SquadronAccessServiceImpl;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentNotFoundException;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentService;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SquadronAccessServiceImplTest {
  @Mock SquadronAssignmentService squadronAssignmentService;
  @Mock SquadronGuestAssignmentService squadronGuestAssignmentService;

  SquadronAccessServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new SquadronAccessServiceImpl(squadronAssignmentService, squadronGuestAssignmentService);
  }

  @Nested
  class GetSquadronAccessByUserId {
    @Test
    void getByUserId_composesPrimaryAndGuestAccess() {
      UUID userId = UUID.randomUUID();
      UUID primarySquadronId = UUID.randomUUID();
      SquadronAssignmentDto primary =
          new SquadronAssignmentDto(primarySquadronId, userId, SquadronRole.INSTRUCTOR);

      SquadronGuestAssignmentDto guestDto =
          new SquadronGuestAssignmentDto(UUID.randomUUID(), UUID.randomUUID(), SquadronRole.OPS);

      when(squadronAssignmentService.getSquadronAssignmentByUserId(userId)).thenReturn(primary);
      when(squadronGuestAssignmentService.getSquadronGuestAssignmentsByUserId(userId))
          .thenReturn(List.of(guestDto));

      UserSquadronAccessDto result = service.getSquadronAccessByUserId(userId);

      assertThat(result)
          .isEqualTo(
              new UserSquadronAccessDto(
                  primarySquadronId, userId, SquadronRole.INSTRUCTOR, List.of(guestDto)));
    }

    @Test
    void getByUserId_noGuestAccess_returnsEmptyGuestList() {
      UUID userId = UUID.randomUUID();
      UUID primarySquadronId = UUID.randomUUID();
      SquadronAssignmentDto primary =
          new SquadronAssignmentDto(primarySquadronId, userId, SquadronRole.STUDENT);

      when(squadronAssignmentService.getSquadronAssignmentByUserId(userId)).thenReturn(primary);
      when(squadronGuestAssignmentService.getSquadronGuestAssignmentsByUserId(userId))
          .thenReturn(List.of());

      UserSquadronAccessDto result = service.getSquadronAccessByUserId(userId);

      assertThat(result.guestAssignmentDtos()).isEmpty();
    }

    @Test
    void getByUserId_noPrimaryAssignment_propagatesNotFoundException() {
      UUID userId = UUID.randomUUID();
      when(squadronAssignmentService.getSquadronAssignmentByUserId(userId))
          .thenThrow(new SquadronAssignmentNotFoundException("not found"));

      assertThatThrownBy(() -> service.getSquadronAccessByUserId(userId))
          .isInstanceOf(SquadronAssignmentNotFoundException.class);
    }
  }

  @Nested
  class GetSquadronAccessBySquadronId {
    @Test
    void getBySquadronId_includesPrimaryAndGuestMembers_returnsComposedDtos() {
      UUID squadronId = UUID.randomUUID();
      UUID primaryUserId = UUID.randomUUID();
      UUID guestUserId = UUID.randomUUID();

      SquadronAssignmentDto primaryAssignment =
          new SquadronAssignmentDto(squadronId, primaryUserId, SquadronRole.INSTRUCTOR);
      SquadronGuestAssignmentDto guestAssignment =
          new SquadronGuestAssignmentDto(squadronId, guestUserId, SquadronRole.OPS);

      when(squadronAssignmentService.getSquadronAssignmentsBySquadronId(squadronId))
          .thenReturn(List.of(primaryAssignment));
      when(squadronGuestAssignmentService.getSquadronGuestAssignmentsBySquadronId(squadronId))
          .thenReturn(List.of(guestAssignment));

      List<SquadronAccessDto> result = service.getSquadronAccessBySquadronId(squadronId);

      assertThat(result)
          .containsExactlyInAnyOrder(
              new SquadronAccessDto(squadronId, primaryUserId, SquadronRole.INSTRUCTOR),
              new SquadronAccessDto(squadronId, guestUserId, SquadronRole.OPS, true));
    }

    @Test
    void getBySquadronId_noMembers_returnsEmptyList() {
      UUID squadronId = UUID.randomUUID();

      when(squadronAssignmentService.getSquadronAssignmentsBySquadronId(squadronId))
          .thenReturn(List.of());
      when(squadronGuestAssignmentService.getSquadronGuestAssignmentsBySquadronId(squadronId))
          .thenReturn(List.of());

      assertThat(service.getSquadronAccessBySquadronId(squadronId)).isEmpty();
    }
  }
}
