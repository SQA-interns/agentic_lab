package si.konferenca.registration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/** Architecture rules A-1 to A-7 of specification section 2 (AR-02, AR-03, ES-01, DoD-04). */
@AnalyzeClasses(
    packages = "si.konferenca.registration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String API = "si.konferenca.registration.api..";
  private static final String APPLICATION = "si.konferenca.registration.application..";
  private static final String DOMAIN = "si.konferenca.registration.domain..";
  private static final String INFRASTRUCTURE = "si.konferenca.registration.infrastructure..";
  private static final String CONFIG = "si.konferenca.registration.config..";

  @ArchTest
  static final ArchRule a1DomainIsIndependent =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(API, APPLICATION, INFRASTRUCTURE, CONFIG, "org.springframework..")
          .as("A-1 domain depends on no other layer and not on Spring");

  @ArchTest
  static final ArchRule a2ApplicationDependsOnlyOnDomain =
      noClasses()
          .that()
          .resideInAPackage(APPLICATION)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(API, INFRASTRUCTURE, CONFIG)
          .as("A-2 application does not depend on api, infrastructure or config");

  @ArchTest
  static final ArchRule a3ApiDoesNotUseInfrastructure =
      noClasses()
          .that()
          .resideInAPackage(API)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(INFRASTRUCTURE)
          .as("A-3 api does not depend on infrastructure");

  @ArchTest
  static final ArchRule a4InfrastructureDoesNotUseApiOrConfig =
      noClasses()
          .that()
          .resideInAPackage(INFRASTRUCTURE)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(API, CONFIG)
          .as("A-4 infrastructure does not depend on api or config");

  @ArchTest
  static final ArchRule a5ControllersLiveInApi =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .resideInAPackage(API)
          .as("A-5 REST controllers reside in api");

  @ArchTest
  static final ArchRule a5RepositoriesLiveInInfrastructure =
      classes()
          .that()
          .areAssignableTo(Repository.class)
          .should()
          .resideInAPackage(INFRASTRUCTURE)
          .as("A-5 Spring Data repositories reside in infrastructure");

  @ArchTest
  static final ArchRule a6NoCycles =
      slices()
          .matching("si.konferenca.registration.(*)..")
          .should()
          .beFreeOfCycles()
          .as("A-6 no cycles between packages (AR-03)");

  @ArchTest
  static final ArchRule a7OnlyConfigReadsTheEnvironment =
      noClasses()
          .that()
          .resideOutsideOfPackage(CONFIG)
          .should()
          .callMethod(System.class, "getenv")
          .orShould()
          .callMethod(System.class, "getenv", String.class)
          .orShould()
          .callMethod(System.class, "getProperty", String.class)
          .orShould()
          .callMethod(System.class, "getProperty", String.class, String.class)
          .as("A-7 only config reads environment variables or system properties (ES-01)");
}
