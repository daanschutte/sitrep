package com.camelbytes.sitrep.integration.squadrons.assignment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.camelbytes.sitrep.AbstractIntegrationTests;
import com.camelbytes.sitrep.squadrons.api.SquadronRole;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignment;
import com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignmentRepository;
import com.camelbytes.sitrep.squadrons.internal.squadron.Squadron;
import com.camelbytes.sitrep.squadrons.internal.squadron.SquadronRepository;
import com.camelbytes.sitrep.users.internal.User;
import com.camelbytes.sitrep.users.internal.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class SquadronAssignmentControllerTest extends AbstractIntegrationTests {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SquadronAssignmentRepository assignmentRepository;
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
    void getByUserId_returnsAssignment() throws Exception {
      SquadronAssignment assignment = new SquadronAssignment(userId, squadronId, SquadronRole.INSTRUCTOR);
      assignmentRepository.save(assignment);

      mockMvc
          .perform(get("/api/v1/squadrons/assignments").param("userId", userId.toString()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(userId.toString()))
          .andExpect(jsonPath("$.primarySquadronId").value(squadronId.toString()))
          .andExpect(jsonPath("$.role").value("INSTRUCTOR"));
    }

    @Test
    void getByUserId_notFound_returnsNotFound() throws Exception {
      mockMvc
          .perform(get("/api/v1/squadrons/assignments").param("userId", UUID.randomUUID().toString()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class GetBySquadronId {
    @Test
    void getBySquadronId_returnsCurrentAssignments() throws Exception {
      SquadronAssignment assignment = new SquadronAssignment(userId, squadronId, SquadronRole.INSTRUCTOR);
      assignmentRepository.save(assignment);

      mockMvc
          .perform(get("/api/v1/squadrons/{id}/assignments", squadronId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].userId").value(userId.toString()));
    }

    @Test
    void getBySquadronId_noAssignments_returnsEmptyList() throws Exception {
      mockMvc
          .perform(get("/api/v1/squadrons/{id}/assignments", squadronId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isEmpty());
    }
  }

  @Nested
  class CreateSquadronAssignment {
    @Test
    void createSquadronAssignment_creates_returnsCreated() throws Exception {
      String body = objectMapper.writeValueAsString(
          new com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignmentCreateRequest(
              userId, SquadronRole.STUDENT));

      mockMvc
          .perform(post("/api/v1/squadrons/{id}/assignments", squadronId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isCreated());
    }

    @Test
    void createSquadronAssignment_transfersExisting_returnsCreated() throws Exception {
      SquadronAssignment existing = new SquadronAssignment(userId, squadronId, SquadronRole.INSTRUCTOR);
      assignmentRepository.save(existing);

      UUID newSquadronId = squadronRepository.save(new Squadron("2 Squadron", "2SQN")).getId();
      String body = objectMapper.writeValueAsString(
          new com.camelbytes.sitrep.squadrons.internal.assignment.SquadronAssignmentCreateRequest(
              userId, SquadronRole.STUDENT));

      mockMvc
          .perform(post("/api/v1/squadrons/{id}/assignments", newSquadronId)
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isCreated());
    }
  }

  @AfterEach
  void tearDown() {
    assignmentRepository.deleteAll();
    userRepository.deleteAll();
    squadronRepository.deleteAll();
  }
}
