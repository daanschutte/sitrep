package dev.bravozulu.sitrep.integration.squadrons.access;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.bravozulu.sitrep.AbstractIntegrationTests;
import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignment;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentRepository;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignment;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentRepository;
import dev.bravozulu.sitrep.squadrons.internal.squadron.Squadron;
import dev.bravozulu.sitrep.squadrons.internal.squadron.SquadronRepository;
import dev.bravozulu.sitrep.users.internal.User;
import dev.bravozulu.sitrep.users.internal.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class SquadronAccessControllerTest extends AbstractIntegrationTests {
  @Autowired private MockMvc mockMvc;
  @Autowired private SquadronAssignmentRepository assignmentRepository;
  @Autowired private SquadronGuestAssignmentRepository guestAssignmentRepository;
  @Autowired private SquadronRepository squadronRepository;
  @Autowired private UserRepository userRepository;

  private UUID squadronId;
  private UUID userId;

  @BeforeEach
  void setUp() {
    Squadron squadron = new Squadron("1 Squadron", "1SQN");
    squadronRepository.save(squadron);
    squadronId = squadron.getId();

    User user = new User("Chuck", "Yeager", "sonic@boom.com", "Gen");
    userRepository.save(user);
    userId = user.getId();
  }

  @Nested
  class GetByUserId {
    @Test
    void getByUserId_primaryOnly_returnsAccessWithEmptyGuestList() throws Exception {
      assignmentRepository.save(
          new SquadronAssignment(squadronId, userId, SquadronRole.INSTRUCTOR));

      mockMvc
          .perform(get("/api/v1/squadrons/assignments").param("userId", userId.toString()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(userId.toString()))
          .andExpect(jsonPath("$.primarySquadronId").value(squadronId.toString()))
          .andExpect(jsonPath("$.role").value("INSTRUCTOR"))
          .andExpect(jsonPath("$.guestAssignmentDtos").isEmpty());
    }

    @Test
    void getByUserId_withGuestAccess_includesGuestAssignments() throws Exception {
      assignmentRepository.save(
          new SquadronAssignment(squadronId, userId, SquadronRole.INSTRUCTOR));

      UUID guestSquadronId = squadronRepository.save(new Squadron("2 Squadron", "2SQN")).getId();
      guestAssignmentRepository.save(
          new SquadronGuestAssignment(guestSquadronId, userId, SquadronRole.OPS));

      mockMvc
          .perform(get("/api/v1/squadrons/assignments").param("userId", userId.toString()))
          .andExpect(status().isOk())
          .andExpect(
              jsonPath("$.guestAssignmentDtos[0].squadronId").value(guestSquadronId.toString()))
          .andExpect(jsonPath("$.guestAssignmentDtos[0].role").value("OPS"));
    }

    @Test
    void getByUserId_noPrimaryAssignment_returnsNotFound() throws Exception {
      mockMvc
          .perform(
              get("/api/v1/squadrons/assignments").param("userId", UUID.randomUUID().toString()))
          .andExpect(status().isNotFound());
    }

    @Test
    void getByUserId_missingParam_returnsBadRequest() throws Exception {
      mockMvc.perform(get("/api/v1/squadrons/assignments")).andExpect(status().isBadRequest());
    }

    @Test
    void getByUserId_malformedUuid_returnsBadRequest() throws Exception {
      mockMvc
          .perform(get("/api/v1/squadrons/assignments").param("userId", "not-a-uuid"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  class GetBySquadronId {
    @Test
    void getBySquadronId_includesPrimaryMember_returnsAccess() throws Exception {
      assignmentRepository.save(
          new SquadronAssignment(squadronId, userId, SquadronRole.INSTRUCTOR));

      mockMvc
          .perform(get("/api/v1/squadrons/{squadronId}/assignments", squadronId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].userId").value(userId.toString()))
          .andExpect(jsonPath("$[0].squadronId").value(squadronId.toString()))
          .andExpect(jsonPath("$[0].role").value("INSTRUCTOR"));
    }

    @Test
    void getBySquadronId_includesGuestMember_returnsAccess() throws Exception {
      UUID guestUserId = userRepository.save(new User("Amelia", "Earhart", "amelia@ae.com", "Capt")).getId();
      guestAssignmentRepository.save(new SquadronGuestAssignment(squadronId, guestUserId, SquadronRole.OPS));

      mockMvc
          .perform(get("/api/v1/squadrons/{squadronId}/assignments", squadronId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].userId").value(guestUserId.toString()))
          .andExpect(jsonPath("$[0].squadronId").value(squadronId.toString()))
          .andExpect(jsonPath("$[0].role").value("OPS"));
    }

    @Test
    void getBySquadronId_noMembers_returnsEmptyList() throws Exception {
      mockMvc
          .perform(get("/api/v1/squadrons/{squadronId}/assignments", squadronId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isEmpty());
    }
  }

  @AfterEach
  void tearDown() {
    guestAssignmentRepository.deleteAll();
    assignmentRepository.deleteAll();
    userRepository.deleteAll();
    squadronRepository.deleteAll();
  }
}
