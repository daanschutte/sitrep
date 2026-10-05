package dev.bravozulu.sitrep.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.time.Instant;
import java.time.LocalDateTime;

@AnalyzeClasses(
    packages = "dev.bravozulu.sitrep.auth",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class AuthArchitectureRulesTests {
  @ArchTest
  static final ArchRule time_shouldComeFromInjectedClock =
      noClasses()
          .should()
          .callMethod(Instant.class, "now")
          .orShould()
          .callMethod(LocalDateTime.class, "now")
          .because("token expiry must be testable with a controlled Clock");
}
