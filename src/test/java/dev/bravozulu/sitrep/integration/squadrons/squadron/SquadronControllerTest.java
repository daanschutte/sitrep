package dev.bravozulu.sitrep.integration.squadrons.squadron;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.bravozulu.sitrep.AbstractIntegrationTests;
import dev.bravozulu.sitrep.squadrons.internal.squadron.Squadron;
import dev.bravozulu.sitrep.squadrons.internal.squadron.SquadronCreateRequest;
import dev.bravozulu.sitrep.squadrons.internal.squadron.SquadronRepository;
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
public class SquadronControllerTest extends AbstractIntegrationTests {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private SquadronRepository repository;

  private UUID id;

  @BeforeEach
  void setUp() {
    Squadron squadron = new Squadron("1 Squadron", "1SQN");
    repository.save(squadron);
    this.id = squadron.getId();
  }

  @Nested
  class GetSquadronById {
    @Test
    void getSquadronById_returnsSquadronDto() throws Exception {
      mockMvc
          .perform(get("/api/v1/squadrons/{id}", id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void getSquadronById_unknown_returnsNotFound() throws Exception {
      mockMvc
          .perform((get("/api/v1/squadrons/{id}", UUID.randomUUID())))
          .andExpect(status().isNotFound());
    }

    @Test
    void getSquadronById_invalidId_returnsBasRequest() throws Exception {
      mockMvc
          .perform(get("/api/v1/squadrons/{id}", "invalidUUID"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  class CreateSquadron {
    @Test
    void createSquadron_createsSquadron() throws Exception {
      SquadronCreateRequest request = new SquadronCreateRequest("2 Squadron", "2SQN");
      MvcResult result =
          mockMvc
              .perform(
                  post("/api/v1/squadrons")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isCreated())
              .andReturn();

      String location = result.getResponse().getHeader("Location");
      UUID createdId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

      mockMvc
          .perform(get("/api/v1/squadrons/{id}", createdId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(createdId.toString()))
          .andExpect(jsonPath("$.name").value(request.name()))
          .andExpect(jsonPath("$.shortName").value(request.shortName()))
          .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void createSquadron_emptyName_returnsUnprocessableContent() throws Exception {
      SquadronCreateRequest request = new SquadronCreateRequest("", "2SQN");

      mockMvc
          .perform(
              post("/api/v1/squadrons")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    void createSquadron_emptyShortName_returnsUnprocessableContent() throws Exception {
      SquadronCreateRequest request = new SquadronCreateRequest("2 Squadron", "");

      mockMvc
          .perform(
              post("/api/v1/squadrons")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    void createSquadron_nullShortName_createsSquadron() throws Exception {
      SquadronCreateRequest request = new SquadronCreateRequest("2 Squadron", null);

      mockMvc
          .perform(
              post("/api/v1/squadrons")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated());
    }

    @Test
    void createSquadron_duplicateName_returnsConflict() throws Exception {
      SquadronCreateRequest request = new SquadronCreateRequest("1 Squadron", "1");

      mockMvc
          .perform(
              post("/api/v1/squadrons")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict());
    }

    @Test
    void createSquadron_duplicateShortName_returnsConflict() throws Exception {
      SquadronCreateRequest request = new SquadronCreateRequest("2 Squadron", "1SQN");

      mockMvc
          .perform(
              post("/api/v1/squadrons")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict());
    }
  }

  @Nested
  class EnableSquadron {
    @Test
    void enableSquadron_enablesSquadron() throws Exception {
      mockMvc.perform(put("/api/v1/squadrons/{id}/disable", id)).andExpect(status().isNoContent());
      mockMvc
          .perform(get("/api/v1/squadrons/{id}", id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id.toString()))
          .andExpect(jsonPath("$.isActive").value(false));

      mockMvc.perform(put("/api/v1/squadrons/{id}/enable", id)).andExpect(status().isNoContent());

      mockMvc
          .perform(get("/api/v1/squadrons/{id}", id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id.toString()))
          .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void enableSquadron_alreadyEnabled_returnsNoContent() throws Exception {
      mockMvc.perform(put("/api/v1/squadrons/{id}/enable", id)).andExpect(status().isNoContent());

      mockMvc
          .perform(get("/api/v1/squadrons/{id}", id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id.toString()))
          .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    void enableSquadron_doesNotExist_returnsNotFound() throws Exception {
      mockMvc
          .perform(put("/api/v1/squadrons/{id}/enable", UUID.randomUUID()))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  class DisableSquadron {
    @Test
    void disableSquadron_disablesSquadron() throws Exception {
      mockMvc.perform(put("/api/v1/squadrons/{id}/disable", id)).andExpect(status().isNoContent());

      mockMvc
          .perform(get("/api/v1/squadrons/{id}", id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id.toString()))
          .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void disableSquadron_alreadyDisable_returnsNoContent() throws Exception {
      mockMvc.perform(put("/api/v1/squadrons/{id}/disable", id)).andExpect(status().isNoContent());
      mockMvc.perform(put("/api/v1/squadrons/{id}/disable", id)).andExpect(status().isNoContent());

      mockMvc
          .perform(get("/api/v1/squadrons/{id}", id))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id.toString()))
          .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void disableSquadron_doesNotExist_returnsNotFound() throws Exception {
      mockMvc
          .perform(put("/api/v1/squadrons/{id}/disable", UUID.randomUUID()))
          .andExpect(status().isNotFound());
    }
  }

  @AfterEach
  void tearDown() {
    repository.deleteAll();
  }
}
