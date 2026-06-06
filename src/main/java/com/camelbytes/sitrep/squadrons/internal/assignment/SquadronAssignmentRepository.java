package com.camelbytes.sitrep.squadrons.internal.assignment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SquadronAssignmentRepository extends JpaRepository<SquadronAssignment, UUID> {
  Optional<SquadronAssignment> findSquadronAssignmentByUserId(UUID id);
}
