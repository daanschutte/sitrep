package com.camelbytes.sitrep;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

public class ModulithVerificationTest {
  @Test
  void modulith_boundaries_are_enforced() {
    ApplicationModules.of(SitrepApplication.class).verify();
  }
}
