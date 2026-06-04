package com.camelbytes.sitrep.squadrons.internal;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SquadronRepository extends JpaRepository<Squadron, UUID> {}
