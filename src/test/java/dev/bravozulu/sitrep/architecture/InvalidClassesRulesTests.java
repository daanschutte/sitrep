package dev.bravozulu.sitrep.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "dev.bravozulu.sitrep")
public class InvalidClassesRulesTests {
  @ArchTest
  static final ArchRule systemOut_shouldNotBeCalled =
      noClasses().should().accessField(System.class, "out");
}
