package dev.bravozulu.sitrep.squadrons.internal.squadron;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SquadronRepository extends JpaRepository<Squadron, UUID> {}
