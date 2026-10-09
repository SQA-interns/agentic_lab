package si.konferenca.registration.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** AR-02 (the declaration in docs/02_specification.md section 2) and AR-03 (no cycles). */
class ArchitectureTest {

  private static final String ROOT = "si.konferenca.registration";
  private static final String DOMAIN = ROOT + ".domain..";
  private static final String APPLICATION = ROOT + ".application..";
  private static final String API = ROOT + ".api..";
  private static final String ADAPTER = ROOT + ".adapter..";
  private static final String CONFIG = ROOT + ".config..";
  private static final List<String> ADAPTERS =
      List.of("persistence", "jsoncopy", "mail", "captcha", "export");

  private static JavaClasses classes;

  @BeforeAll
  static void importClasses() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);
  }

  @Test
  void ar02_domainDependsOnNothingElseInTheApplication() {
    noClasses()
        .that()
        .resideInAPackage(DOMAIN)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(APPLICATION, API, ADAPTER, CONFIG)
        .check(classes);
  }

  @Test
  void ar02_applicationDependsOnlyOnTheDomain() {
    noClasses()
        .that()
        .resideInAPackage(APPLICATION)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(API, ADAPTER, CONFIG)
        .check(classes);
  }

  @Test
  void ar02_apiDoesNotUseAdaptersOrConfiguration() {
    noClasses()
        .that()
        .resideInAPackage(API)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(ADAPTER, CONFIG)
        .check(classes);
  }

  @Test
  void ar02_adaptersDoNotUseTheApiConfigurationOrEachOther() {
    noClasses()
        .that()
        .resideInAPackage(ADAPTER)
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(API, CONFIG)
        .check(classes);
    for (String adapter : ADAPTERS) {
      String[] others =
          ADAPTERS.stream()
              .filter(a -> !a.equals(adapter))
              .map(a -> ROOT + ".adapter." + a + "..")
              .toArray(String[]::new);
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".adapter." + adapter + "..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(others)
          .check(classes);
    }
  }

  @Test
  void ar01_onlyTheApiServesHttp() {
    noClasses()
        .that()
        .resideOutsideOfPackage(API)
        .should()
        .beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
        .check(classes);
  }

  @Test
  void ar03_packagesAreFreeOfCycles() {
    slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(classes);
    slices().matching(ROOT + ".adapter.(*)..").should().beFreeOfCycles().check(classes);
  }
}
