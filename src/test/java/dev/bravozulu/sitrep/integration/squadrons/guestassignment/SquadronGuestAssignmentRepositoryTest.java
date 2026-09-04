package dev.bravozulu.sitrep.integration.squadrons.guestassignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import dev.bravozulu.sitrep.AbstractIntegrationTests;
import dev.bravozulu.sitrep.squadrons.api.SquadronRole;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignment;
import dev.bravozulu.sitrep.squadrons.internal.guestassignment.SquadronGuestAssignmentRepository;
import dev.bravozulu.sitrep.squadrons.internal.squadron.Squadron;
import dev.bravozulu.sitrep.squadrons.internal.squadron.SquadronRepository;
import dev.bravozulu.sitrep.users.internal.User;
import dev.bravozulu.sitrep.users.internal.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * There is currently no DB-level constraint preventing two active guest-assignment rows for the
 * same (user, squadron) pair - unlike squadron_assignment's partial unique index. This test expects
 * that constraint to exist (a migration adding a unique index on (user_id, squadron_id) WHERE
 * revoked_at IS NULL) and will fail until it's added.
 */
public class SquadronGuestAssignmentRepositoryTest extends AbstractIntegrationTests {
  @Autowired private SquadronGuestAssignmentRepository repository;
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

  @Test
  void save_duplicateActiveGuestAssignment_violatesUniqueConstraint() {
    repository.saveAndFlush(new SquadronGuestAssignment(squadronId, userId, SquadronRole.OPS));

    assertThatThrownBy(
            () ->
                repository.saveAndFlush(
                    new SquadronGuestAssignment(squadronId, userId, SquadronRole.STUDENT)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void save_sameUserDifferentSquadrons_bothPersist() {
    UUID otherSquadronId = squadronRepository.save(new Squadron("2 Squadron", "2SQN")).getId();

    repository.saveAndFlush(new SquadronGuestAssignment(squadronId, userId, SquadronRole.OPS));
    repository.saveAndFlush(
        new SquadronGuestAssignment(otherSquadronId, userId, SquadronRole.STUDENT));

    assertThat(repository.findAllByUserIdAndRevokedAtIsNull(userId)).hasSize(2);
  }

  @AfterEach
  void tearDown() {
    repository.deleteAll();
    userRepository.deleteAll();
    squadronRepository.deleteAll();
  }
}
