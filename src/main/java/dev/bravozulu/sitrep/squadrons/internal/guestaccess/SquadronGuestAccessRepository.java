package dev.bravozulu.sitrep.squadrons.internal.guestaccess;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SquadronGuestAccessRepository extends JpaRepository<SquadronGuestAccess, UUID> {
  Optional<SquadronGuestAccess> findBySquadronIdAndUserIdAndRevokedAtIsNull(
      UUID squadronId, UUID userId);

  List<SquadronGuestAccess> findAllByUserIdAndRevokedAtIsNull(UUID userId);
}
