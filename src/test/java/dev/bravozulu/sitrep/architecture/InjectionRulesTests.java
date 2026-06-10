package dev.bravozulu.sitrep.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.beans.factory.annotation.Autowired;

@AnalyzeClasses(
    packages = "dev.bravozulu.sitrep",
    importOptions = ImportOption.DoNotIncludeTests.class)
public class InjectionRulesTests {
  @ArchTest
  static final ArchRule autowired_notAllowed = noFields().should().beAnnotatedWith(Autowired.class);
}
