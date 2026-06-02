package com.camelbytes.sitrep.squadrons.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SquadronRepository extends JpaRepository<Squadron, UUID> {}
