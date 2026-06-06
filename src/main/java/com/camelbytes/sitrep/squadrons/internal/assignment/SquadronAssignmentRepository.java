package com.camelbytes.sitrep.squadrons.internal.assignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SquadronAssignmentRepository extends JpaRepository<SquadronAssignment, UUID> {
  Optional<SquadronAssignment> findByUserId(UUID id);

  Optional<SquadronAssignment> findByUserIdAndEndedAtIsNull(UUID uuid);

  List<SquadronAssignment> findBySquadronIdAndEndedAtIsNull(UUID squadronId);
}
