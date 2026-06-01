package com.camelbytes.sitrep.unit.users;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.camelbytes.sitrep.AbstractIntegrationTests;
import com.camelbytes.sitrep.users.internal.User;
import com.camelbytes.sitrep.users.internal.UserCreateRequest;
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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class UserControllerTest extends AbstractIntegrationTests {
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository repository;

  private UUID userId;

  @BeforeEach
  void setUp() {
    User user = new User("Chuck", "Yeager", "sonic@boom.com", "Gen");
    repository.save(user);
    this.userId = user.getId();
  }

  @Nested
  class GetUserById {
    @Test
    void getUserById_returnsUser() throws Exception {
      mockMvc
          .perform(get("/api/v1/users/{id}", userId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    void getUserById_unknown_returnsNotFound() throws Exception {
      mockMvc
          .perform((get("/api/v1/users/{id}", UUID.randomUUID())))
          .andExpect(status().isNotFound());
    }

    @Test
    void getUserById_invalidId_returnsBasRequest() throws Exception {
      mockMvc.perform(get("/api/v1/users/{id}", "invalidUUID")).andExpect(status().isBadRequest());
    }
  }

  @Nested
  class CreateUser {
    @Test
    void createUser_createsUser() throws Exception {
      UserCreateRequest request =
          new UserCreateRequest("Chuck", "Yeager", "speedy@jetpilot.com", "Gen");

      MvcResult result =
          mockMvc
              .perform(
                  post("/api/v1/users")
                      .contentType(MediaType.APPLICATION_JSON)
                      .content(objectMapper.writeValueAsString(request)))
              .andExpect(status().isCreated())
              .andReturn();

      String location = result.getResponse().getHeader("Location");
      UUID createdId = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

      mockMvc
          .perform(get("/api/v1/users/{id}", createdId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(createdId.toString()))
          .andExpect(jsonPath("$.firstName").value(request.firstName()))
          .andExpect(jsonPath("$.lastName").value(request.lastName()))
          .andExpect(jsonPath("$.email").value(request.email()))
          .andExpect(jsonPath("$.rank").value(request.rank()));
    }

    @Test
    void createUser_emptyFirstName_returnsUnprocessableContent() throws Exception {
      UserCreateRequest request =
          new UserCreateRequest("", "Yeager", "speedy@jetpilot.com", "Gen");

      mockMvc
          .perform(
              post("/api/v1/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    void createUser_emptyLastName_returnsUnprocessableContent() throws Exception {
      UserCreateRequest request = new UserCreateRequest("Chuck", "", "speedy@jetpilot.com", "Gen");

      mockMvc
          .perform(
              post("/api/v1/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    void createUser_emptyEmail_returnsUnprocessableContent() throws Exception {
      UserCreateRequest request = new UserCreateRequest("Chuck", "Yeager", "", "Gen");

      mockMvc
          .perform(
              post("/api/v1/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    void createUser_duplicateEmail_returnsConflict() throws Exception {
      UserCreateRequest request =
          new UserCreateRequest("Chuck", "Yeager", "sonic@boom.com", "Gen");

      mockMvc
          .perform(
              post("/api/v1/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isConflict());
    }

    @Test
    void createUser_emptyRank_returnsUnprocessableContent() throws Exception {
      UserCreateRequest request =
          new UserCreateRequest("Chuck", "Yeager", "speedy@jetpilot.com", "");

      mockMvc
          .perform(
              post("/api/v1/users")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnprocessableContent());
    }
  }

  @AfterEach
  void tearDown() {
    repository.deleteAll();
  }
}
