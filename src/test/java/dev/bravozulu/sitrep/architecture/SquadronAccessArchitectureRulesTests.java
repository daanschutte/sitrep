package dev.bravozulu.sitrep.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Guards the coordinator shape agreed for squadron access: `internal.assignment` (primary
 * assignment) must stay independent of `internal.guestassignment` (guest access) to avoid a
 * circular service dependency. The reverse dependency (guest -> primary, for the "not your own
 * primary squadron" check) is intentional and not restricted here. Composition of both lives in
 * `internal.access`.
 */
@AnalyzeClasses(packages = "dev.bravozulu.sitrep.squadrons")
public class SquadronAccessArchitectureRulesTests {
  @ArchTest
  static final ArchRule assignment_shouldNotDependOn_guestAssignment =
      noClasses()
          .that()
          .resideInAPackage("..squadrons.internal.assignment..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("..squadrons.internal.guestassignment..");
}
