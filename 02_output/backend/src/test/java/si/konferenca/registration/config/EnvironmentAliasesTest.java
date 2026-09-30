package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EnvironmentAliasesTest {

  @Test
  void mapsOnlyVariablesThatAreSet() {
    Map<String, Object> mapped =
        EnvironmentAliases.map(
            Map.of(
                "ORGANIZER_PASSWORD", "secret-value-1234",
                "DB_URL", "jdbc:postgresql://db/registration",
                "UNRELATED", "x"));

    assertThat(mapped)
        .containsOnly(
            Map.entry("app.organizer.password", "secret-value-1234"),
            Map.entry("spring.datasource.url", "jdbc:postgresql://db/registration"));
  }

  @Test
  void everyDocumentedSecretHasAnAlias() {
    assertThat(EnvironmentAliases.ALIASES)
        .containsKeys(
            "POSTGRES_PASSWORD",
            "ORGANIZER_USERNAME",
            "ORGANIZER_PASSWORD",
            "ORGANIZER_EMAILS",
            "RECAPTCHA_SITE_KEY",
            "RECAPTCHA_SECRET_KEY",
            "SMTP_USERNAME",
            "SMTP_PASSWORD");
  }

  @Test
  void addsNoSourceWhenNothingIsSet() {
    MockEnvironment environment = new MockEnvironment();
    int before = environment.getPropertySources().size();

    new EnvironmentAliases().postProcessEnvironment(environment, null);

    assertThat(environment.getPropertySources().size())
        .isEqualTo(
            before
                + (EnvironmentAliases.map(environment.getSystemEnvironment()).isEmpty() ? 0 : 1));
  }
}
