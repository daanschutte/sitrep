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
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentRoleChangeRequest;
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
  class AssignGuestSquadron {
    @Test
    void assignGuestSquadron_creates_returnsCreated() throws Exception {
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
    void assignGuestSquadron_persistsCorrectUserAndSquadronIds() throws Exception {
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
    void assignGuestSquadron_targetIsPrimarySquadron_returnsConflict() throws Exception {
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
    void assignGuestSquadron_alreadyActive_returnsConflict() throws Exception {
      guestAssignmentRepository.save(
          new SquadronGuestAssignment(squadronId, userId, SquadronRole.OPS));

      String body =
          objectMapper.writeValueAsString(
              new SquadronGuestAssignmentCreateRequest(userId, SquadronRole.STUDENT));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/guest-assignments", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isConflict());
    }

    @Test
    void assignGuestSquadron_nonExistentUser_returnsNotFound() throws Exception {
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
    void assignGuestSquadron_nonExistentSquadron_returnsNotFound() throws Exception {
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
    void assignGuestSquadron_invalidBody_returnsUnprocessableContent() throws Exception {
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
    void assignGuestSquadron_malformedUuid_returnsBadRequest() throws Exception {
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
  class ChangeGuestSquadronRole {
    @Test
    void changeGuestSquadronRole_found_returnsNoContent() throws Exception {
      SquadronGuestAssignment assignment =
          new SquadronGuestAssignment(squadronId, userId, SquadronRole.STUDENT);
      guestAssignmentRepository.save(assignment);

      String body =
          objectMapper.writeValueAsString(
              new SquadronGuestAssignmentRoleChangeRequest(SquadronRole.OPS));

      mockMvc
          .perform(
              put(
                      "/api/v1/squadrons/{squadronId}/guest-assignments/{userId}/role",
                      squadronId,
                      userId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isNoContent());

      SquadronGuestAssignment updated =
          guestAssignmentRepository.findById(assignment.getId()).orElseThrow();
      assertThat(updated.getRole()).isEqualTo(SquadronRole.OPS);
    }

    @Test
    void changeGuestSquadronRole_notFound_returnsNotFound() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronGuestAssignmentRoleChangeRequest(SquadronRole.OPS));

      mockMvc
          .perform(
              put(
                      "/api/v1/squadrons/{squadronId}/guest-assignments/{userId}/role",
                      squadronId,
                      UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isNotFound());
    }

    @Test
    void changeGuestSquadronRole_invalidBody_returnsUnprocessableContent() throws Exception {
      mockMvc
          .perform(
              put(
                      "/api/v1/squadrons/{squadronId}/guest-assignments/{userId}/role",
                      squadronId,
                      userId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"role\": null}"))
          .andExpect(status().isUnprocessableContent());
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

    @Test
    void revokeSquadronGuestAssignment_alreadyRevoked_returnsNotFound() throws Exception {
      guestAssignmentRepository.save(
          new SquadronGuestAssignment(squadronId, userId, SquadronRole.OPS));

      mockMvc
          .perform(
              put(
                  "/api/v1/squadrons/{squadronId}/guest-assignments/{userId}/revoke",
                  squadronId,
                  userId))
          .andExpect(status().isNoContent());

      mockMvc
          .perform(
              put(
                  "/api/v1/squadrons/{squadronId}/guest-assignments/{userId}/revoke",
                  squadronId,
                  userId))
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
