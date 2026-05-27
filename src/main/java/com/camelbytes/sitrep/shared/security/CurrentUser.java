package com.camelbytes.sitrep.shared.security;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface CurrentUser {
    UUID userId();
    UUID currentSquadronId();
    List<UUID> accessibleSquadronIds();
    Set<String> roles();
}
