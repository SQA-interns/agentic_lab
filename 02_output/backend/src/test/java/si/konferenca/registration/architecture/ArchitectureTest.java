package si.konferenca.registration.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.Repository;
import org.springframework.web.bind.annotation.RestController;

/** The architecture declared in `docs/02_specification.md` section 2 (AR-02, AR-03, AR-06). */
@AnalyzeClasses(
    packages = "si.konferenca.registration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final String ROOT = "si.konferenca.registration";

  @ArchTest
  static final ArchRule layers =
      layeredArchitecture()
          .consideringOnlyDependenciesInLayers()
          .layer("web")
          .definedBy(ROOT + ".web..")
          .layer("service")
          .definedBy(ROOT + ".service..")
          .layer("persistence")
          .definedBy(ROOT + ".persistence..")
          .layer("integration")
          .definedBy(ROOT + ".integration..")
          .layer("config")
          .definedBy(ROOT + ".config..")
          .layer("domain")
          .definedBy(ROOT + ".domain..")
          .whereLayer("web")
          .mayNotBeAccessedByAnyLayer()
          .whereLayer("service")
          .mayOnlyBeAccessedByLayers("web")
          .whereLayer("persistence")
          .mayOnlyBeAccessedByLayers("service")
          .whereLayer("integration")
          .mayOnlyBeAccessedByLayers("service")
          .whereLayer("config")
          .mayOnlyBeAccessedByLayers("web", "service", "persistence", "integration")
          .whereLayer("domain")
          .mayOnlyBeAccessedByLayers("web", "service", "persistence", "integration", "config");

  @ArchTest
  static final ArchRule noPackageCycles =
      slices().matching(ROOT + ".(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule controllersOnlyInWeb =
      classes()
          .that()
          .areAnnotatedWith(RestController.class)
          .should()
          .resideInAPackage(ROOT + ".web..");

  @ArchTest
  static final ArchRule repositoriesOnlyInPersistence =
      classes()
          .that()
          .areAssignableTo(Repository.class)
          .should()
          .resideInAPackage(ROOT + ".persistence..");

  @ArchTest
  static final ArchRule mailAndHttpClientOnlyInIntegration =
      noClasses()
          .that()
          .resideOutsideOfPackage(ROOT + ".integration..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework.mail..", "java.net.http..");

  @ArchTest
  static final ArchRule poiOnlyInService =
      noClasses()
          .that()
          .resideOutsideOfPackage(ROOT + ".service..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.apache.poi..");

  @ArchTest
  static final ArchRule domainUsesNoSpring =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".domain..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..");

  @ArchTest
  static final ArchRule rootHoldsOnlyTheApplication =
      classes().that().resideInAPackage(ROOT).should().haveSimpleName("RegistrationApplication");

  /** AR-06: the only migration is the storage contract, unchanged. */
  @Test
  void migrationIsTheStorageContract() throws IOException {
    Path migration = Path.of("src/main/resources/db/migration");
    try (Stream<Path> files = Files.list(migration)) {
      assertThat(files.map(p -> p.getFileName().toString()))
          .containsExactly("V1__registration.sql");
    }
    assertThat(migration.resolve("V1__registration.sql"))
        .hasSameBinaryContentAs(Path.of("../docs/02_contracts/registration-storage.sql"));
  }
}
