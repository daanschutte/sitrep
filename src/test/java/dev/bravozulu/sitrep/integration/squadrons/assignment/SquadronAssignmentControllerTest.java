package dev.bravozulu.sitrep.integration.squadrons.assignment;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.bravozulu.sitrep.AbstractIntegrationTests;
import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignment;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentCreateRequest;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentRepository;
import dev.bravozulu.sitrep.squadrons.internal.squadron.Squadron;
import dev.bravozulu.sitrep.squadrons.internal.squadron.SquadronRepository;
import dev.bravozulu.sitrep.users.internal.User;
import dev.bravozulu.sitrep.users.internal.UserRepository;
import java.util.List;
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
import org.springframework.test.web.servlet.MvcResult;
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
  class CreateSquadronAssignment {
    @Test
    void createSquadronAssignment_creates_returnsCreated() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentCreateRequest(userId, SquadronRole.STUDENT));

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/v1/squadrons/{squadronId}/assignments", squadronId)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andExpect(status().isCreated())
              .andReturn();

      String location = result.getResponse().getHeader("Location");
      String expected = String.format("/api/v1/squadrons/%s/assignments", squadronId);
      assertThat(location).contains(expected);
    }

    @Test
    void createSquadronAssignment_transfersExisting_returnsCreated() throws Exception {
      SquadronAssignment existing =
          new SquadronAssignment(squadronId, userId, SquadronRole.INSTRUCTOR);
      assignmentRepository.save(existing);

      UUID newSquadronId = squadronRepository.save(new Squadron("2 Squadron", "2SQN")).getId();
      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentCreateRequest(userId, SquadronRole.STUDENT));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/assignments", newSquadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isCreated());

      SquadronAssignment updatedExisting =
          assignmentRepository.findById(existing.getId()).orElseThrow();
      assertThat(updatedExisting.getRevokedAt()).isPresent();

      List<SquadronAssignment> currentAssignments =
          assignmentRepository.findByUserIdAndRevokedAtIsNull(userId).stream().toList();
      assertThat(currentAssignments.size()).isEqualTo(1);
      assertThat(currentAssignments.getFirst().getSquadronId()).isEqualTo(newSquadronId);
    }

    @Test
    void createSquadronAssignment_nonExistentUser_returnsNotFound() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.STUDENT));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/assignments", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isNotFound());
    }

    @Test
    void createSquadronAssignment_nonExistentSquadron_returnsNotFound() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentCreateRequest(userId, SquadronRole.STUDENT));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/assignments", UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isNotFound());
    }

    @Test
    void createSquadronAssignment_invalidBody_returnsUnprocessableContent() throws Exception {
      String nullUserId = "{\"userId\": null, \"role\": \"STUDENT\"}";

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/assignments", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(nullUserId))
          .andExpect(status().isUnprocessableContent());

      String nullRole = String.format("{\"userId\": \"%s\", \"role\": null}", userId);

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/assignments", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(nullRole))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    void createSquadronAssignment_malformedUuid_returnsBadRequest() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentCreateRequest(userId, SquadronRole.STUDENT));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/assignments", "not-a-uuid")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  class RevokeSquadronAssignment {
    @Test
    void revokeSquadronAssignment_found_returnsNoContent() throws Exception {
      SquadronAssignment assignment =
          new SquadronAssignment(squadronId, userId, SquadronRole.STUDENT);
      assignmentRepository.save(assignment);

      mockMvc
          .perform(
              put("/api/v1/squadrons/{squadronId}/assignments/{userId}/revoke", squadronId, userId))
          .andExpect(status().isNoContent());

      SquadronAssignment updated = assignmentRepository.findById(assignment.getId()).orElseThrow();
      assertThat(updated.getRevokedAt()).isPresent();
    }

    @Test
    void revokeSquadronAssignment_notFound_returnsNotFound() throws Exception {
      mockMvc
          .perform(
              put(
                  "/api/v1/squadrons/{squadronId}/assignments/{userId}/revoke",
                  squadronId,
                  UUID.randomUUID()))
          .andExpect(status().isNotFound());
    }
  }

  @AfterEach
  void tearDown() {
    assignmentRepository.deleteAll();
    userRepository.deleteAll();
    squadronRepository.deleteAll();
  }
}
