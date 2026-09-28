package si.konferenca.registration;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Enforces the preconditions of the triaged dependency findings in {@code
 * dependency-check-suppressions.xml}: the affected Spring features are neither used by the
 * application nor present on the runtime classpath.
 */
@AnalyzeClasses(
    packages = "si.konferenca.registration",
    importOptions = ImportOption.DoNotIncludeTests.class)
class VulnerableFeatureGuardTest {

  @ArchTest
  static final ArchRule noViewRenderingSpelOrReactiveFeatures =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework.web.servlet.view..",
              "org.springframework.web.servlet.function..",
              "org.springframework.web.servlet.mvc.method.annotation..",
              "org.springframework.expression..",
              "org.springframework.web.reactive..",
              "org.springframework.messaging.rsocket..",
              "org.springframework.web.socket..",
              "org.springframework.security.ldap..",
              "org.springframework.security.oauth2..",
              "org.springframework.security.web.webauthn..");

  @ArchTest
  static final ArchRule noParameterDataBindingOntoObjects =
      noClasses().should().dependOnClassesThat().areAssignableTo(ModelAttribute.class);

  @Test
  void vulnerableModulesAreNotOnTheClasspath() {
    List<String> absentClasses =
        List.of(
            "org.springframework.web.reactive.DispatcherHandler",
            "org.springframework.messaging.rsocket.RSocketRequester",
            "org.springframework.web.socket.WebSocketHandler",
            "org.springframework.security.ldap.server.UnboundIdContainer",
            "org.springframework.security.oauth2.jwt.DPoPProofJwtDecoderFactory",
            "org.eclipse.jetty.server.Server");
    for (String className : absentClasses) {
      assertThat(isPresent(className)).as(className).isFalse();
    }
  }

  private static boolean isPresent(String className) {
    try {
      Class.forName(className, false, VulnerableFeatureGuardTest.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
