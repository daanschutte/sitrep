package dev.bravozulu.sitrep.squadrons.internal.guestassignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SquadronGuestAssignmentRepository extends JpaRepository<SquadronGuestAssignment, UUID> {
  Optional<SquadronGuestAssignment> findBySquadronIdAndUserIdAndRevokedAtIsNull(
      UUID squadronId, UUID userId);

  List<SquadronGuestAssignment> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
