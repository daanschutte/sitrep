package dev.bravozulu.sitrep.squadrons.internal.guestassignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SquadronGuestAssignmentRepository
    extends JpaRepository<SquadronGuestAssignment, UUID> {
  Optional<SquadronGuestAssignment> findBySquadronIdAndUserIdAndRevokedAtIsNull(
      UUID squadronId, UUID userId);

  List<SquadronGuestAssignment> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
