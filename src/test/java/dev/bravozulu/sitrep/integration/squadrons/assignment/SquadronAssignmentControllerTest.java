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
import dev.bravozulu.sitrep.squadrons.internal.assignment.SquadronAssignmentRoleChangeRequest;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignment;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentRepository;
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
  class AssignSquadron {
    @Test
    void assignSquadron_creates_returnsCreated() throws Exception {
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
    void assignSquadron_alreadyAssigned_returnsConflict() throws Exception {
      assignmentRepository.save(
          new SquadronAssignment(squadronId, userId, SquadronRole.INSTRUCTOR));

      UUID newSquadronId = squadronRepository.save(new Squadron("2 Squadron", "2SQN")).getId();
      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentCreateRequest(userId, SquadronRole.STUDENT));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/assignments", newSquadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isConflict());
    }

    @Test
    void assignSquadron_nonExistentUser_returnsNotFound() throws Exception {
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
    void assignSquadron_nonExistentSquadron_returnsNotFound() throws Exception {
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
    void assignSquadron_invalidBody_returnsUnprocessableContent() throws Exception {
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
    void assignSquadron_existingGuestAccessToSameSquadron_revokesGuestAccess() throws Exception {
      SquadronGuestAssignment guestAssignment =
          new SquadronGuestAssignment(squadronId, userId, SquadronRole.OPS);
      guestAssignmentRepository.save(guestAssignment);

      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentCreateRequest(userId, SquadronRole.STUDENT));

      mockMvc
          .perform(
              post("/api/v1/squadrons/{squadronId}/assignments", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isCreated());

      SquadronGuestAssignment updatedGuestAssignment =
          guestAssignmentRepository.findById(guestAssignment.getId()).orElseThrow();
      assertThat(updatedGuestAssignment.isActive()).isFalse();
    }

    @Test
    void assignSquadron_malformedUuid_returnsBadRequest() throws Exception {
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
  class TransferSquadronAssignment {
    @Test
    void transferSquadronAssignment_existingAssignment_movesToNewSquadron() throws Exception {
      SquadronAssignment existing =
          new SquadronAssignment(squadronId, userId, SquadronRole.INSTRUCTOR);
      assignmentRepository.save(existing);

      UUID newSquadronId = squadronRepository.save(new Squadron("2 Squadron", "2SQN")).getId();
      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentCreateRequest(userId, SquadronRole.STUDENT));

      mockMvc
          .perform(
              put("/api/v1/squadrons/{squadronId}/assignments/transfer", newSquadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isNoContent());

      SquadronAssignment updatedExisting =
          assignmentRepository.findById(existing.getId()).orElseThrow();
      assertThat(updatedExisting.getRevokedAt()).isInThePast();

      List<SquadronAssignment> currentAssignments =
          assignmentRepository.findByUserIdAndRevokedAtIsNull(userId).stream().toList();
      assertThat(currentAssignments.size()).isEqualTo(1);
      assertThat(currentAssignments.getFirst().getSquadronId()).isEqualTo(newSquadronId);
    }

    @Test
    void transferSquadronAssignment_noExistingAssignment_returnsNotFound() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentCreateRequest(userId, SquadronRole.STUDENT));

      mockMvc
          .perform(
              put("/api/v1/squadrons/{squadronId}/assignments/transfer", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isNotFound());
    }

    @Test
    void transferSquadronAssignment_existingGuestAccessToTargetSquadron_revokesGuestAccess()
        throws Exception {
      assignmentRepository.save(new SquadronAssignment(squadronId, userId, SquadronRole.STUDENT));

      UUID newSquadronId = squadronRepository.save(new Squadron("2 Squadron", "2SQN")).getId();
      SquadronGuestAssignment guestAssignment =
          new SquadronGuestAssignment(newSquadronId, userId, SquadronRole.OPS);
      guestAssignmentRepository.save(guestAssignment);

      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentCreateRequest(userId, SquadronRole.STUDENT));

      mockMvc
          .perform(
              put("/api/v1/squadrons/{squadronId}/assignments/transfer", newSquadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isNoContent());

      SquadronGuestAssignment updatedGuestAssignment =
          guestAssignmentRepository.findById(guestAssignment.getId()).orElseThrow();
      assertThat(updatedGuestAssignment.isActive()).isFalse();
    }

    @Test
    void transferSquadronAssignment_sameSquadron_returnsConflict() throws Exception {
      assignmentRepository.save(
          new SquadronAssignment(squadronId, userId, SquadronRole.INSTRUCTOR));

      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentCreateRequest(userId, SquadronRole.STUDENT));

      mockMvc
          .perform(
              put("/api/v1/squadrons/{squadronId}/assignments/transfer", squadronId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isConflict());
    }
  }

  @Nested
  class ChangeSquadronRole {
    @Test
    void changeSquadronRole_found_returnsNoContent() throws Exception {
      SquadronAssignment assignment =
          new SquadronAssignment(squadronId, userId, SquadronRole.STUDENT);
      assignmentRepository.save(assignment);

      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentRoleChangeRequest(SquadronRole.INSTRUCTOR));

      mockMvc
          .perform(
              put("/api/v1/squadrons/{squadronId}/assignments/{userId}/role", squadronId, userId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isNoContent());

      SquadronAssignment updated = assignmentRepository.findById(assignment.getId()).orElseThrow();
      assertThat(updated.getRole()).isEqualTo(SquadronRole.INSTRUCTOR);
    }

    @Test
    void changeSquadronRole_notFound_returnsNotFound() throws Exception {
      String body =
          objectMapper.writeValueAsString(
              new SquadronAssignmentRoleChangeRequest(SquadronRole.INSTRUCTOR));

      mockMvc
          .perform(
              put(
                      "/api/v1/squadrons/{squadronId}/assignments/{userId}/role",
                      squadronId,
                      UUID.randomUUID())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isNotFound());
    }

    @Test
    void changeSquadronRole_invalidBody_returnsUnprocessableContent() throws Exception {
      mockMvc
          .perform(
              put("/api/v1/squadrons/{squadronId}/assignments/{userId}/role", squadronId, userId)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"role\": null}"))
          .andExpect(status().isUnprocessableContent());
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
      assertThat(updated.getRevokedAt()).isInThePast();
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

    @Test
    void revokeSquadronAssignment_alreadyRevoked_returnsNotFound() throws Exception {
      SquadronAssignment assignment =
          new SquadronAssignment(squadronId, userId, SquadronRole.STUDENT);
      assignmentRepository.save(assignment);

      mockMvc
          .perform(
              put("/api/v1/squadrons/{squadronId}/assignments/{userId}/revoke", squadronId, userId))
          .andExpect(status().isNoContent());

      mockMvc
          .perform(
              put("/api/v1/squadrons/{squadronId}/assignments/{userId}/revoke", squadronId, userId))
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
