package si.konferenca.registration.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * The declared backend architecture (specification section 3): four layers whose dependencies point
 * inwards only, adapters that do not know each other, and no package cycles (AR-02, AR-03).
 */
class ArchitectureTest {

  private static final String ROOT = "si.konferenca.registration";
  private static final JavaClasses PRODUCTION =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(ROOT);

  @Test
  void ar02_domainDependsOnTheJdkOnly() {
    classes()
        .that()
        .resideInAPackage(ROOT + ".domain..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(ROOT + ".domain..", "java..")
        .check(PRODUCTION);
  }

  @Test
  void ar02_applicationDependsOnTheDomainOnly() {
    classes()
        .that()
        .resideInAPackage(ROOT + ".application..")
        .should()
        .onlyDependOnClassesThat()
        .resideInAnyPackage(ROOT + ".application..", ROOT + ".domain..", "java..")
        .check(PRODUCTION);
  }

  @Test
  void ar02_webAdapterUsesTheApplicationAndDomainOnly() {
    noClasses()
        .that()
        .resideInAPackage(ROOT + ".adapter.in..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(ROOT + ".adapter.out..", ROOT + ".config..")
        .check(PRODUCTION);
  }

  @Test
  void ar02_outgoingAdaptersDependOnTheDomainOnly() {
    noClasses()
        .that()
        .resideInAPackage(ROOT + ".adapter.out..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(ROOT + ".adapter.in..", ROOT + ".application..", ROOT + ".config..")
        .check(PRODUCTION);
  }

  @Test
  void ar02_outgoingAdaptersDoNotDependOnEachOther() {
    slices()
        .matching(ROOT + ".adapter.out.(*)..")
        .should()
        .notDependOnEachOther()
        .check(PRODUCTION);
  }

  @Test
  void ar02_nothingDependsOnTheConfiguration() {
    noClasses()
        .that()
        .resideOutsideOfPackage(ROOT + ".config..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(ROOT + ".config..")
        .check(PRODUCTION);
  }

  @Test
  void ar03_packagesAreFreeOfCycles() {
    slices().matching(ROOT + ".(**)").should().beFreeOfCycles().check(PRODUCTION);
  }

  @Test
  void ar06_onlyThePersistenceAdapterUsesJpa() {
    noClasses()
        .that()
        .resideOutsideOfPackage(ROOT + ".adapter.out.persistence..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
        .check(PRODUCTION);
  }
}
