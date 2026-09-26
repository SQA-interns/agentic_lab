package si.konferenca.registration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

/** Verifies the architecture declared in docs/specification.md §2.2. */
@AnalyzeClasses(
    packages = "si.konferenca.registration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String BASE = "si.konferenca.registration";

  @ArchTest
  static final ArchRule layers =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Web")
          .definedBy(BASE + ".web..")
          .layer("Application")
          .definedBy(BASE + ".application..")
          .layer("Domain")
          .definedBy(BASE + ".domain..")
          .layer("Persistence")
          .definedBy(BASE + ".persistence..")
          .layer("Infrastructure")
          .definedBy(BASE + ".infrastructure..")
          .layer("Config")
          .definedBy(BASE + ".config..")
          .whereLayer("Web")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Infrastructure")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Web", "Infrastructure")
          .whereLayer("Persistence")
          .mayOnlyBeAccessedByLayers("Application")
          .whereLayer("Config")
          .mayOnlyBeAccessedByLayers("Web", "Application", "Infrastructure")
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers(
              "Web", "Application", "Persistence", "Infrastructure", "Config");

  @ArchTest
  static final ArchRule webDoesNotUsePersistenceOrInfrastructure =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".web..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".persistence..", BASE + ".infrastructure..");

  @ArchTest
  static final ArchRule applicationDoesNotUseWebOrInfrastructure =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(BASE + ".web..", BASE + ".infrastructure..");

  @ArchTest
  static final ArchRule domainIsIndependent =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".web..",
              BASE + ".application..",
              BASE + ".persistence..",
              BASE + ".infrastructure..",
              BASE + ".config..");

  @ArchTest
  static final ArchRule configDependsOnlyOnDomain =
      noClasses()
          .that()
          .resideInAPackage(BASE + ".config..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              BASE + ".web..",
              BASE + ".application..",
              BASE + ".persistence..",
              BASE + ".infrastructure..");

  @ArchTest
  static final ArchRule noPackageCycles =
      slices().matching(BASE + ".(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule controllersLiveInWeb =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .resideInAPackage(BASE + ".web..");
}
