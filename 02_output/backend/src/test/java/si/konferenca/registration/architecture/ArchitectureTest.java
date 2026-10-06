package si.konferenca.registration.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/** The declared architecture of specification section 2 (AR-02, AR-03, DoD-04). */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("si.konferenca.registration");

  @Test
  void layersOnlyUseThePermittedLayers() {
    layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("web")
        .definedBy("..registration.web..")
        .layer("application")
        .definedBy("..registration.application..")
        .layer("domain")
        .definedBy("..registration.domain..")
        .layer("infrastructure")
        .definedBy("..registration.infrastructure..")
        .layer("config")
        .definedBy("..registration.config..")
        .whereLayer("config")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("web")
        .mayOnlyBeAccessedByLayers("config")
        .whereLayer("infrastructure")
        .mayOnlyBeAccessedByLayers("config")
        .whereLayer("application")
        .mayOnlyBeAccessedByLayers("web", "infrastructure", "config")
        .check(CLASSES);
  }

  @Test
  void domainHasNoFrameworkDependencies() {
    noClasses()
        .that()
        .resideInAPackage("..registration.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..",
            "jakarta.persistence..",
            "org.apache.poi..",
            "tools.jackson..",
            "..registration.application..",
            "..registration.infrastructure..",
            "..registration.web..")
        .check(CLASSES);
  }

  @Test
  void webDoesNotUseInfrastructure() {
    noClasses()
        .that()
        .resideInAPackage("..registration.web..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("..registration.infrastructure..")
        .check(CLASSES);
  }

  @Test
  void entitiesLiveOnlyInPersistence() {
    classes()
        .that()
        .areAnnotatedWith(jakarta.persistence.Entity.class)
        .should()
        .resideInAPackage("..infrastructure.persistence..")
        .check(CLASSES);
  }

  @Test
  void noPackageCycles() {
    slices().matching("si.konferenca.registration.(*)..").should().beFreeOfCycles().check(CLASSES);
  }
}
