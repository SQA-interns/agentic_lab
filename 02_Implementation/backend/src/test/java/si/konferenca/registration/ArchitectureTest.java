package si.konferenca.registration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

/** Verifies the layered architecture with ports declared in specification §2.1. */
@AnalyzeClasses(
    packages = "si.konferenca.registration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String BASE = "si.konferenca.registration";

  @ArchTest
  static final ArchRule domainDependsOnNoOtherApplicationPackage =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".service..",
              BASE + ".api..",
              BASE + ".infrastructure..",
              BASE + ".security..",
              BASE + ".config..");

  @ArchTest
  static final ArchRule serviceDependsOnlyOnDomain =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".service..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".api..",
              BASE + ".infrastructure..",
              BASE + ".security..",
              BASE + ".config..");

  @ArchTest
  static final ArchRule apiDoesNotUseInfrastructureOrSecurity =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".infrastructure..", BASE + ".security..");

  @ArchTest
  static final ArchRule infrastructureDoesNotUseApiOrSecurity =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".api..", BASE + ".security..");

  @ArchTest
  static final ArchRule controllersResideInApi =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .resideInAPackage(BASE + ".api..");

  @ArchTest
  static final ArchRule entitiesResideInDomain =
      classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage(BASE + ".domain..");

  @ArchTest
  static final ArchRule packagesAreFreeOfCycles =
      slices().matching(BASE + ".(*)..").should().beFreeOfCycles();
}
