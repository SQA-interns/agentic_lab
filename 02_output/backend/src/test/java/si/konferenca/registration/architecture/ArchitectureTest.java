package si.konferenca.registration.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The declared layering of specification section 2 (AR-02) and no package cycles (AR-03). */
class ArchitectureTest {

  private static final JavaClasses CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages("si.konferenca.registration");

  @Test
  @DisplayName("AR-02 layers depend only as declared in the specification")
  void layers() {
    layeredArchitecture()
        .consideringOnlyDependenciesInLayers()
        .layer("api")
        .definedBy("..registration.api..")
        .layer("service")
        .definedBy("..registration.service..")
        .layer("domain")
        .definedBy("..registration.domain..")
        .layer("persistence")
        .definedBy("..registration.persistence..")
        .layer("infrastructure")
        .definedBy("..registration.infrastructure..")
        .layer("config")
        .definedBy("..registration.config..")
        .whereLayer("api")
        .mayNotBeAccessedByAnyLayer()
        .whereLayer("service")
        .mayOnlyBeAccessedByLayers("api")
        .whereLayer("persistence")
        .mayOnlyBeAccessedByLayers("service")
        .whereLayer("infrastructure")
        .mayOnlyBeAccessedByLayers("service")
        .whereLayer("config")
        .mayOnlyBeAccessedByLayers("api", "service", "infrastructure")
        .whereLayer("domain")
        .mayOnlyBeAccessedByLayers("api", "service", "persistence", "infrastructure", "config")
        .check(CLASSES);
  }

  @Test
  @DisplayName("AR-02 the domain uses no framework types")
  void domainIsFrameworkFree() {
    noClasses()
        .that()
        .resideInAPackage("..registration.domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "tools.jackson..")
        .check(CLASSES);
  }

  @Test
  @DisplayName("AR-03 no package cycles")
  void noCycles() {
    slices().matching("si.konferenca.registration.(*)..").should().beFreeOfCycles().check(CLASSES);
  }

  @Test
  @DisplayName("AR-06 only Flyway changes the schema: Hibernate validates, never updates")
  void noSchemaGenerationInCode() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .haveFullyQualifiedName("org.hibernate.tool.hbm2ddl.SchemaExport")
        .check(CLASSES);
  }
}
