package si.konferenca.registration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The architecture declared in docs/02_specification.md, section 2 (AR-02, AR-03). */
@AnalyzeClasses(
    packages = "si.konferenca.registration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String ROOT = "si.konferenca.registration";

  @ArchTest
  static final ArchRule layers =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("api")
          .definedBy(ROOT + ".api..")
          .layer("application")
          .definedBy(ROOT + ".application..")
          .layer("domain")
          .definedBy(ROOT + ".domain..")
          .layer("infrastructure")
          .definedBy(ROOT + ".infrastructure..")
          .layer("config")
          .definedBy(ROOT + ".config..")
          .whereLayer("api")
          .mayOnlyBeAccessedByLayers("config")
          .whereLayer("infrastructure")
          .mayOnlyBeAccessedByLayers("config")
          .whereLayer("application")
          .mayOnlyBeAccessedByLayers("api", "infrastructure", "config")
          .whereLayer("domain")
          .mayOnlyBeAccessedByLayers("api", "application", "infrastructure", "config")
          .whereLayer("config")
          .mayNotBeAccessedByAnyLayer();

  @ArchTest
  static final ArchRule noCycles = slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule apiUsesNoPersistence =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".api..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("jakarta.persistence..", "org.springframework.jdbc..");

  @ArchTest
  static final ArchRule onlyInfrastructureUsesMailPoiAndHttpClient =
      noClasses()
          .that()
          .resideOutsideOfPackage(ROOT + ".infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("jakarta.mail..", "org.apache.poi..", "java.net.http..");
}
