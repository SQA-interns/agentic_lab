package org.example.conference.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** AR-02 / spec §8: declared module boundaries and absence of cycles. */
@AnalyzeClasses(
    packages = "org.example.conference",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String BASE = "org.example.conference.";

  @ArchTest
  static final ArchRule modulesAreFreeOfCycles =
      slices().matching("org.example.conference.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule moduleDependencyMatrix =
      layeredArchitecture()
          .consideringOnlyDependenciesInAnyPackage("org.example.conference..")
          .layer("app")
          .definedBy("org.example.conference")
          .layer("shared")
          .definedBy(BASE + "shared..")
          .layer("catalog")
          .definedBy(BASE + "catalog..")
          .layer("captcha")
          .definedBy(BASE + "captcha..")
          .layer("notification")
          .definedBy(BASE + "notification..")
          .layer("registration")
          .definedBy(BASE + "registration..")
          .layer("export")
          .definedBy(BASE + "export..")
          .whereLayer("export")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("registration")
          .mayOnlyBeAccessedByLayers("export")
          .whereLayer("catalog")
          .mayOnlyBeAccessedByLayers("registration")
          .whereLayer("captcha")
          .mayOnlyBeAccessedByLayers("registration")
          .whereLayer("notification")
          .mayOnlyBeAccessedByLayers("registration")
          .whereLayer("shared")
          .mayOnlyBeAccessedByLayers(
              "catalog", "captcha", "notification", "registration", "export");

  @ArchTest
  static final ArchRule exportUsesOnlyTheRegistrationQueryApi =
      noClasses()
          .that()
          .resideInAPackage(BASE + "export..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              BASE + "registration.domain..",
              BASE + "registration.backup..",
              BASE + "registration.api..");

  @ArchTest
  static final ArchRule controllersAreNotUsedByOtherCode =
      noClasses()
          .that()
          .resideOutsideOfPackages(BASE + "registration.api..", BASE + "export..")
          .should()
          .dependOnClassesThat()
          .haveSimpleNameEndingWith("Controller");

  @ArchTest
  static final ArchRule noHibernateAutoDdlOrFieldInjection =
      noClasses()
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("org.springframework.beans.factory.annotation.Autowired");
}
