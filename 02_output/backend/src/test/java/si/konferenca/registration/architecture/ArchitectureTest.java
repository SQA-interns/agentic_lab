package si.konferenca.registration.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import jakarta.persistence.Entity;
import org.springframework.web.bind.annotation.RestController;

/**
 * The declared backend architecture, rules A1 to A6 (02_specification.md section 2; AR-02, AR-03).
 */
@AnalyzeClasses(
    packages = "si.konferenca.registration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String ROOT = "si.konferenca.registration";

  @ArchTest
  static final ArchRule a1DomainDependsOnNoOtherLayer =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              ROOT + ".application..",
              ROOT + ".infrastructure..",
              ROOT + ".web..",
              ROOT + ".config..",
              ROOT + ".settings..");

  @ArchTest
  static final ArchRule a2ApplicationDependsOnlyOnDomain =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              ROOT + ".infrastructure..",
              ROOT + ".web..",
              ROOT + ".config..",
              ROOT + ".settings..");

  @ArchTest
  static final ArchRule a3InfrastructureAndWebAreIndependent =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".infrastructure..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ROOT + ".web..")
          .orShould()
          .dependOnClassesThat()
          .resideInAPackage(ROOT + ".config..");

  @ArchTest
  static final ArchRule a3WebDoesNotUseInfrastructure =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".web..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(ROOT + ".infrastructure..", ROOT + ".config..");

  @ArchTest
  static final ArchRule a4NothingDependsOnConfig =
      noClasses()
          .that()
          .resideOutsideOfPackage(ROOT + ".config..")
          .and()
          .resideOutsideOfPackage(ROOT)
          .should()
          .dependOnClassesThat()
          .resideInAPackage(ROOT + ".config..");

  @ArchTest
  static final ArchRule a4SettingsDependOnNothingInTheApplication =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".settings..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              ROOT + ".domain..",
              ROOT + ".application..",
              ROOT + ".infrastructure..",
              ROOT + ".web..",
              ROOT + ".config..");

  @ArchTest
  static final ArchRule a5NoPackageCycles =
      slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule a6ControllersLiveInWeb =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .resideInAPackage(ROOT + ".web..");

  @ArchTest
  static final ArchRule a6EntitiesLiveInDomain =
      classes().that().areAnnotatedWith(Entity.class).should().resideInAPackage(ROOT + ".domain..");
}
