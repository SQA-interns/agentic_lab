package si.konferenca.registration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The declared architecture of specification section 2 (AR-02, AR-03). */
@AnalyzeClasses(
    packages = "si.konferenca.registration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule layers =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Api")
          .definedBy("..registration.api..")
          .layer("Application")
          .definedBy("..registration.application..")
          .layer("Domain")
          .definedBy("..registration.domain..")
          .layer("Infrastructure")
          .definedBy("..registration.infrastructure..")
          .layer("Config")
          .definedBy("..registration.config..")
          .whereLayer("Api")
          .mayOnlyBeAccessedByLayers("Config")
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Api", "Infrastructure", "Config")
          .whereLayer("Infrastructure")
          .mayOnlyBeAccessedByLayers("Config")
          .whereLayer("Config")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Domain")
          .mayOnlyBeAccessedByLayers("Api", "Application", "Infrastructure", "Config");

  @ArchTest
  static final ArchRule noCycles =
      slices().matching("si.konferenca.registration.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule coreHasNoWebDependencies =
      noClasses()
          .that()
          .resideInAnyPackage("..registration.application..", "..registration.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("jakarta.servlet..", "org.springframework.web..");

  @ArchTest
  static final ArchRule domainUsesOnlyPersistenceLibraries =
      noClasses()
          .that()
          .resideInAPackage("..registration.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.transaction..",
              "org.springframework.mail..",
              "org.apache.poi..",
              "tools.jackson..",
              "si.konferenca.registration.application..");

  @ArchTest
  static final ArchRule externalLibrariesOnlyInInfrastructure =
      noClasses()
          .that()
          .resideOutsideOfPackages("..registration.infrastructure..", "..registration.config..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.apache.poi..", "jakarta.mail..", "org.springframework.mail..", "org.flywaydb..")
          .orShould()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.web.client.RestClient");
}
