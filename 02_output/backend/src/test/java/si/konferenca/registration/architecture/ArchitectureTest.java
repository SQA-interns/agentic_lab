package si.konferenca.registration.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** The backend architecture declared in docs/02_specification.md §2 (AR-02, AR-03). */
@AnalyzeClasses(
    packages = "si.konferenca.registration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String ROOT = "si.konferenca.registration.";

  @ArchTest
  static final ArchRule ar02_layersOnlyUseTheDeclaredDependencies =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("Domain")
          .definedBy(ROOT + "domain..")
          .layer("Application")
          .definedBy(ROOT + "application..")
          .layer("Web")
          .definedBy(ROOT + "web..")
          .layer("Infrastructure")
          .definedBy(ROOT + "infrastructure..")
          .layer("Config")
          .definedBy(ROOT + "config..")
          .whereLayer("Config")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("Web")
          .mayOnlyBeAccessedByLayers("Config")
          .whereLayer("Infrastructure")
          .mayOnlyBeAccessedByLayers("Config")
          .whereLayer("Application")
          .mayOnlyBeAccessedByLayers("Web", "Infrastructure", "Config");

  @ArchTest
  static final ArchRule ar02_domainDependsOnlyOnTheJavaPlatform =
      classes()
          .that()
          .resideInAPackage(ROOT + "domain..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", ROOT + "domain..");

  @ArchTest
  static final ArchRule ar02_applicationDependsOnlyOnDomainAndJava =
      classes()
          .that()
          .resideInAPackage(ROOT + "application..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", ROOT + "domain..", ROOT + "application..");

  @ArchTest
  static final ArchRule ar02_webDoesNotAccessInfrastructure =
      noClasses()
          .that()
          .resideInAPackage(ROOT + "web..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ROOT + "infrastructure..");

  @ArchTest
  static final ArchRule ar03_packagesAreFreeOfCycles =
      slices().matching(ROOT + "(*)..").should().beFreeOfCycles();
}
