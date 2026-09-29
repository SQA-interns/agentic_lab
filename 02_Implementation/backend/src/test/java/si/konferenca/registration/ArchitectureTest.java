package si.konferenca.registration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/** The layered architecture declared in docs/specification.md §2.1 (rules 1–7). */
@AnalyzeClasses(
    packages = "si.konferenca.registration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String ROOT = "si.konferenca.registration";
  private static final String WEB = ROOT + ".web..";
  private static final String SERVICE = ROOT + ".service..";
  private static final String DOMAIN = ROOT + ".domain..";
  private static final String PERSISTENCE = ROOT + ".persistence..";
  private static final String INTEGRATION = ROOT + ".integration..";
  private static final String CONFIG = ROOT + ".config..";

  @ArchTest
  static final ArchRule domainDependsOnNoOtherApplicationPackage =
      noClasses()
          .that()
          .resideInAPackage(DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(WEB, SERVICE, PERSISTENCE, INTEGRATION, CONFIG);

  @ArchTest
  static final ArchRule persistenceDependsOnlyOnDomain =
      noClasses()
          .that()
          .resideInAPackage(PERSISTENCE)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(WEB, SERVICE, INTEGRATION, CONFIG);

  @ArchTest
  static final ArchRule integrationDependsOnlyOnDomain =
      noClasses()
          .that()
          .resideInAPackage(INTEGRATION)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(WEB, SERVICE, PERSISTENCE, CONFIG);

  @ArchTest
  static final ArchRule webDoesNotReachPersistenceOrIntegration =
      noClasses()
          .that()
          .resideInAPackage(WEB)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(PERSISTENCE, INTEGRATION);

  @ArchTest
  static final ArchRule serviceDoesNotDependOnWebOrConfig =
      noClasses()
          .that()
          .resideInAPackage(SERVICE)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(WEB, CONFIG);

  @ArchTest
  static final ArchRule noPackageCycles =
      slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule controllersLiveInWeb =
      classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage(WEB);

  @ArchTest
  static final ArchRule entitiesLiveInDomain =
      classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage(DOMAIN);

  @ArchTest
  static final ArchRule repositoriesLiveInPersistence =
      classes()
          .that()
          .areAssignableTo(Repository.class)
          .and()
          .areInterfaces()
          .should()
          .resideInAPackage(PERSISTENCE);
}
