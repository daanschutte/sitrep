package dev.bravozulu.sitrep.squadrons.internal.assignment;

import dev.bravozulu.sitrep.squadrons.api.SquadronAssignmentDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SquadronAssignmentRepository extends JpaRepository<SquadronAssignment, UUID> {
  Optional<SquadronAssignment> findByUserIdAndRevokedAtIsNull(UUID uuid);

  List<SquadronAssignmentDto> findAllBySquadronIdAndRevokedAtIsNull(UUID squadronId);

  Optional<SquadronAssignment> findBySquadronIdAndUserIdAndRevokedAtIsNull(
      UUID squadronId, UUID userId);
}
