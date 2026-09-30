package lab.conference;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** AR-02 module boundaries and the D-11 "unused vulnerable features" constraint. */
class ArchitectureTest {

  private static JavaClasses classes;

  @BeforeAll
  static void load() {
    classes =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("lab.conference");
  }

  @Test
  void modulesHaveNoCycles() {
    slices().matching("lab.conference.(*)..").should().beFreeOfCycles().check(classes);
  }

  @Test
  void platformDependsOnNoFeatureModule() {
    noClasses()
        .that()
        .resideInAPackage("lab.conference.platform..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "lab.conference.options..",
            "lab.conference.registration..",
            "lab.conference.notifications..",
            "lab.conference.export..")
        .check(classes);
  }

  @Test
  void optionsAndNotificationsDependOnlyOnPlatform() {
    noClasses()
        .that()
        .resideInAPackage("lab.conference.options..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "lab.conference.registration..",
            "lab.conference.notifications..",
            "lab.conference.export..")
        .check(classes);
    noClasses()
        .that()
        .resideInAPackage("lab.conference.notifications..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "lab.conference.registration..", "lab.conference.options..", "lab.conference.export..")
        .check(classes);
  }

  @Test
  void registrationDoesNotDependOnExport() {
    noClasses()
        .that()
        .resideInAPackage("lab.conference.registration..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage("lab.conference.export..")
        .check(classes);
  }

  /** D-11: the unpatched Spring CVEs need these features; the application must not use them. */
  @Test
  void vulnerableFrameworkFeaturesAreNotUsed() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.web.servlet.view.xslt..",
            "org.springframework.web.servlet.mvc.method.annotation..SseEmitter",
            "org.springframework.web.reactive..",
            "org.springframework.web.socket..",
            "org.springframework.messaging.rsocket..",
            "org.springframework.expression..",
            "org.springframework.validation.DataBinder",
            "org.springframework.web.bind.annotation.ModelAttribute",
            "org.springframework.web.servlet.function..",
            "org.springframework.security.ldap..",
            "org.springframework.security.oauth2..",
            "org.springframework.security.web.webauthn..")
        .check(classes);
  }

  @Test
  void featureStacksAreNotOnTheClasspath() {
    for (String name :
        new String[] {
          "org.springframework.web.reactive.DispatcherHandler",
          "org.springframework.messaging.rsocket.RSocketRequester",
          "org.springframework.security.ldap.server.UnboundIdContainer",
          "org.springframework.web.socket.WebSocketHandler",
          "org.eclipse.jetty.server.Server",
          "com.fasterxml.aalto.AsyncXMLInputFactory"
        }) {
      org.assertj.core.api.Assertions.assertThatThrownBy(() -> Class.forName(name))
          .as(name)
          .isInstanceOf(ClassNotFoundException.class);
    }
  }
}
