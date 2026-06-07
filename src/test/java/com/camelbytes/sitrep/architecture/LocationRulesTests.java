package com.camelbytes.sitrep.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.camelbytes.sitrep")
public class LocationRulesTests {
  @ArchTest
  static final ArchRule controllers_shouldLiveInPackage_internal =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .resideInAPackage("..internal..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule services_shouldLiveInPackage_internal =
      classes()
          .that()
          .areAnnotatedWith(Service.class)
          .should()
          .resideInAPackage("..internal..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule dtos_shouldLiveInPackage_api =
      classes()
          .that()
          .haveSimpleNameEndingWith("Dto")
          .should()
          .resideInAPackage("..api")
          .allowEmptyShould(true);
}
