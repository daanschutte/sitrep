package com.camelbytes.sitrep.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.camelbytes.sitrep")
public class IORulesTests {
  @ArchTest
  static final ArchRule systemOut_shouldNotBeCalled =
      noClasses().should().accessField(System.class, "out");
}
