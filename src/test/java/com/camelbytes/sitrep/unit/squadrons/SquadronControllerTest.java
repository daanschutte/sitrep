package com.camelbytes.sitrep.unit.squadrons;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.camelbytes.sitrep.AbstractIntegrationTests;
import com.camelbytes.sitrep.squadrons.internal.Squadron;
import com.camelbytes.sitrep.squadrons.internal.SquadronCreateRequest;
import com.camelbytes.sitrep.squadrons.internal.SquadronRepository;
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

  private UUID squadronId;

  @BeforeEach
  void setUp() {
    Squadron squadron = new Squadron("1 Squadron", "1SQN");
    repository.save(squadron);
    this.squadronId = squadron.getId();
  }

  @Nested
  class GetSquadronById {
    @Test
    void getSquadronById_returnsSquadronDto() throws Exception {
      mockMvc
          .perform(get("/api/v1/squadrons/{id}", squadronId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(squadronId.toString()));
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
          .andExpect(jsonPath("$.shortName").value(request.shortName()));
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
    void createSquadron_emptyShortName_createsSquadron() throws Exception {
      SquadronCreateRequest request = new SquadronCreateRequest("2 Squadron", "");

      mockMvc
          .perform(
              post("/api/v1/squadrons")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated());
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
      SquadronCreateRequest request = new SquadronCreateRequest("1 Squadron", "");

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

  @AfterEach
  void tearDown() {
    repository.deleteAll();
  }
}
