package dev.bravozulu.sitrep.integration.squadrons.guestassignment;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.bravozulu.sitrep.AbstractIntegrationTests;
import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignment;
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentRepository;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignment;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentCreateRequest;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/**
 * Targets the guest-assignment controller endpoints as agreed but not yet implemented: POST
 * /api/v1/squadrons/{squadronId}/guest-assignments and PUT
 * /api/v1/squadrons/{squadronId}/guest-assignments/{userId}/revoke, mirroring
 * SquadronAssignmentController. Expect these to fail (404) until the controller exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class SquadronGuestAssignmentControllerTest extends AbstractIntegrationTests {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SquadronGuestAssignmentRepository guestAssignmentRepository;
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
  class CreateSquadronGuestAssignment {
    @Test
    void createSquadronGuestAssignment_creates_returnsCreated() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronGuestAssignmentCreateRequest(userId, SquadronRole.OPS));

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/v1/squadrons/{squadronId}/guest-assignments", squadronId)
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(body))
              .andExpect(status().isCreated())
              .andReturn();

      String location = result.getResponse().getHeader("Location");
      String expected = String.format("/api/v1/squadrons/%s/guest-assignments", squadronId);
      assertThat(location).contains(expected);
    }

    @Test
    void createSquadronGuestAssignment_persistsCorrectUserAndSquadronIds() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronGuestAssignmentCreateRequest(userId, SquadronRole.OPS));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/guest-assignments", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isCreated());

      SquadronGuestAssignment saved =
          guestAssignmentRepository
              .findBySquadronIdAndUserIdAndRevokedAtIsNull(squadronId, userId)
              .orElseThrow();
      assertThat(saved.getUserId()).isEqualTo(userId);
      assertThat(saved.getSquadronId()).isEqualTo(squadronId);
    }

    @Test
    void createSquadronGuestAssignment_targetIsPrimarySquadron_returnsConflict() throws Exception {
      assignmentRepository.save(
          new SquadronAssignment(squadronId, userId, SquadronRole.INSTRUCTOR));

      String body =
          objectMapper.writeValueAsString(
              new SquadronGuestAssignmentCreateRequest(userId, SquadronRole.OPS));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/guest-assignments", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isConflict());
    }

    @Test
    void createSquadronGuestAssignment_nonExistentUser_returnsNotFound() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronGuestAssignmentCreateRequest(UUID.randomUUID(), SquadronRole.OPS));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/guest-assignments", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isNotFound());
    }

    @Test
    void createSquadronGuestAssignment_nonExistentSquadron_returnsNotFound() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronGuestAssignmentCreateRequest(userId, SquadronRole.OPS));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/guest-assignments", UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isNotFound());
    }

    @Test
    void createSquadronGuestAssignment_invalidBody_returnsUnprocessableContent() throws Exception {
      String nullUserId = "{\"userId\": null, \"role\": \"OPS\"}";

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/guest-assignments", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(nullUserId))
          .andExpect(status().isUnprocessableContent());

      String nullRole = String.format("{\"userId\": \"%s\", \"role\": null}", userId);

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/guest-assignments", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(nullRole))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    void createSquadronGuestAssignment_malformedUuid_returnsBadRequest() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronGuestAssignmentCreateRequest(userId, SquadronRole.OPS));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/guest-assignments", "not-a-uuid")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  class RevokeSquadronGuestAssignment {
    @Test
    void revokeSquadronGuestAssignment_found_returnsNoContent() throws Exception {
      SquadronGuestAssignment assignment =
          new SquadronGuestAssignment(squadronId, userId, SquadronRole.OPS);
      guestAssignmentRepository.save(assignment);

      mockMvc
          .perform(
              put(
                  "/api/v1/squadrons/{squadronId}/guest-assignments/{userId}/revoke",
                  squadronId,
                  userId))
          .andExpect(status().isNoContent());

      SquadronGuestAssignment updated =
          guestAssignmentRepository.findById(assignment.getId()).orElseThrow();
      assertThat(updated.isActive()).isFalse();
    }

    @Test
    void revokeSquadronGuestAssignment_notFound_returnsNotFound() throws Exception {
      mockMvc
          .perform(
              put(
                  "/api/v1/squadrons/{squadronId}/guest-assignments/{userId}/revoke",
                  squadronId,
                  UUID.randomUUID()))
          .andExpect(status().isNotFound());
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
