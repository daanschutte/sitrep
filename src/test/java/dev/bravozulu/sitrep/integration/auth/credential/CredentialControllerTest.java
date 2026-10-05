package dev.bravozulu.sitrep.integration.auth.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.bravozulu.sitrep.AbstractIntegrationTests;
import dev.bravozulu.sitrep.auth.internal.SecureTokens;
import dev.bravozulu.sitrep.auth.internal.credential.ActivationToken;
import dev.bravozulu.sitrep.auth.internal.credential.ActivationTokenCreateRequest;
import dev.bravozulu.sitrep.auth.internal.credential.ActivationTokenRepository;
import dev.bravozulu.sitrep.auth.internal.credential.Credential;
import dev.bravozulu.sitrep.auth.internal.credential.CredentialCreateRequest;
import dev.bravozulu.sitrep.auth.internal.credential.CredentialRepository;
import dev.bravozulu.sitrep.auth.internal.credential.CredentialService;
import dev.bravozulu.sitrep.auth.internal.credential.InvalidActivationTokenException;
import dev.bravozulu.sitrep.auth.internal.credential.IssuedActivationToken;
import dev.bravozulu.sitrep.auth.internal.credential.SystemRole;
import dev.bravozulu.sitrep.users.internal.User;
import dev.bravozulu.sitrep.users.internal.UserRepository;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
public class CredentialControllerTest extends AbstractIntegrationTests {
  private static final String PASSWORD = "correct horse battery staple";
  private static final String GENERIC_DETAIL = "invalid or has already been used";

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private CredentialRepository credentialRepository;
  @Autowired private ActivationTokenRepository tokenRepository;
  @Autowired private CredentialService credentialService;

  private UUID userId;

  @BeforeEach
  void setUp() {
    User user = new User("Pete", "Mitchell", "maverick@example.com", "Capt");
    userRepository.save(user);
    userId = user.getId();
  }

  @AfterEach
  void tearDown() {
    credentialRepository.deleteAll();
    tokenRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Nested
  class IssueActivationToken {
    @Test
    void issue_returnsCreatedAndPersistsOnlyTheHash() throws Exception {
      String rawToken = issue(userId, SystemRole.USER);

      List<ActivationToken> tokens = tokenRepository.findAll();
      assertThat(tokens).hasSize(1);
      assertThat(tokens.getFirst().getTokenHash())
          .isEqualTo(SecureTokens.sha256Hex(rawToken))
          .isNotEqualTo(rawToken);
    }

    @Test
    void issue_unknownUser_returnsNotFound() throws Exception {
      postIssue(UUID.randomUUID(), SystemRole.USER).andExpect(status().isNotFound());
    }

    @Test
    void issue_deactivatedUser_returnsConflict() throws Exception {
      deactivateUser();

      postIssue(userId, SystemRole.USER).andExpect(status().isConflict());
    }

    @Test
    void issue_userAlreadyHasCredential_returnsConflict() throws Exception {
      postRedeem(issue(userId, SystemRole.USER), PASSWORD).andExpect(status().isCreated());

      postIssue(userId, SystemRole.USER).andExpect(status().isConflict());
    }

    @Test
    void issue_missingRole_returnsUnprocessableContent() throws Exception {
      postIssue(userId, null).andExpect(status().isUnprocessableContent());
    }

    @Test
    void issue_twice_revokesTheFirstToken() throws Exception {
      String first = issue(userId, SystemRole.USER);
      String second = issue(userId, SystemRole.USER);

      postRedeem(first, PASSWORD).andExpect(status().isBadRequest());
      postRedeem(second, PASSWORD).andExpect(status().isCreated());
    }
  }

  @Nested
  class RedeemActivationToken {
    @Test
    void redeem_validToken_createsCredentialWithTokenRole() throws Exception {
      String rawToken = issue(userId, SystemRole.ADMIN);

      postRedeem(rawToken, PASSWORD).andExpect(status().isCreated());

      Credential credential = credentialRepository.findAll().getFirst();
      assertThat(credential.getUserId()).isEqualTo(userId);
      assertThat(credential.getRole()).isEqualTo(SystemRole.ADMIN);
      assertThat(credential.getPasswordHash()).startsWith("{bcrypt}$2a$12$");
      assertThat(storedToken().getConsumedAt()).isNotNull();
    }

    @Test
    void redeem_sameTokenTwice_returnsGenericBadRequest() throws Exception {
      String rawToken = issue(userId, SystemRole.USER);
      postRedeem(rawToken, PASSWORD).andExpect(status().isCreated());

      postRedeem(rawToken, PASSWORD)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(containsString(GENERIC_DETAIL)));
    }

    @Test
    void redeem_unknownToken_returnsGenericBadRequest() throws Exception {
      postRedeem(SecureTokens.generate(), PASSWORD)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(containsString(GENERIC_DETAIL)));
    }

    @Test
    void redeem_userDeactivatedAfterIssue_returnsBadRequestAndRevokesToken() throws Exception {
      String rawToken = issue(userId, SystemRole.USER);
      deactivateUser();

      postRedeem(rawToken, PASSWORD).andExpect(status().isBadRequest());

      assertThat(storedToken().getRevokedAt()).isNotNull();
      assertThat(credentialRepository.count()).isZero();
    }

    @Test
    void redeem_credentialAlreadyExists_returnsBadRequestAndRevokesToken() throws Exception {
      String rawToken = issue(userId, SystemRole.USER);
      credentialRepository.save(new Credential(userId, "{bcrypt}existing", SystemRole.USER));

      postRedeem(rawToken, PASSWORD).andExpect(status().isBadRequest());

      assertThat(storedToken().getRevokedAt()).isNotNull();
      assertThat(storedToken().getConsumedAt()).isNull();
    }

    @Test
    void redeem_concurrentRedemptions_createExactlyOneCredential() throws Exception {
      String rawToken = issue(userId, SystemRole.USER);
      CountDownLatch start = new CountDownLatch(1);
      Callable<Boolean> redeem =
          () -> {
            start.await();
            try {
              credentialService.redeem(rawToken, PASSWORD);
              return true;
            } catch (InvalidActivationTokenException ex) {
              return false;
            }
          };

      try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
        Future<Boolean> first = executor.submit(redeem);
        Future<Boolean> second = executor.submit(redeem);
        start.countDown();

        assertThat(List.of(result(first), result(second))).containsExactlyInAnyOrder(true, false);
      }
      assertThat(credentialRepository.count()).isOne();
    }
  }

  @Nested
  class PasswordRules {
    @Test
    void redeem_passwordTooShort_returnsUnprocessableContent() throws Exception {
      postRedeem(issue(userId, SystemRole.USER), "a".repeat(11))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    void redeem_passwordOver72AsciiBytes_returnsUnprocessableContent() throws Exception {
      postRedeem(issue(userId, SystemRole.USER), "a".repeat(73))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    void redeem_passwordOver72MultiByteBytes_returnsUnprocessableContent() throws Exception {
      postRedeem(issue(userId, SystemRole.USER), "€".repeat(25))
          .andExpect(status().isUnprocessableContent());
    }

    @Test
    void redeem_passwordExactly72MultiByteBytes_returnsCreated() throws Exception {
      postRedeem(issue(userId, SystemRole.USER), "€".repeat(24)).andExpect(status().isCreated());
    }
  }

  private String issue(UUID userId, SystemRole role) throws Exception {
    String body =
        postIssue(userId, role)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readValue(body, IssuedActivationToken.class).token();
  }

  private ResultActions postIssue(UUID userId, SystemRole role) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/activation-tokens")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(new ActivationTokenCreateRequest(userId, role))));
  }

  private ResultActions postRedeem(String token, String password) throws Exception {
    return mockMvc.perform(
        post("/api/v1/auth/credentials")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(new CredentialCreateRequest(token, password))));
  }

  private ActivationToken storedToken() {
    return tokenRepository.findAll().getFirst();
  }

  private void deactivateUser() {
    User user = userRepository.findById(userId).orElseThrow();
    user.deactivate();
    userRepository.save(user);
  }

  private static boolean result(Future<Boolean> future) throws InterruptedException {
    try {
      return future.get();
    } catch (ExecutionException ex) {
      throw new AssertionError("Redemption failed unexpectedly", ex.getCause());
    }
  }
}
