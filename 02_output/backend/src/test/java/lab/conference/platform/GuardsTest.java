package lab.conference.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class GuardsTest {

  @Test
  void profileMustBeExplicitAndKnown() {
    assertThat(AppProfile.parse(" Production ")).isEqualTo(AppProfile.PRODUCTION);
    assertThat(AppProfile.parse("local").allowsTestSubstitutes()).isTrue();
    assertThat(AppProfile.parse("test").allowsTestSubstitutes()).isTrue();
    assertThat(AppProfile.PRODUCTION.allowsTestSubstitutes()).isFalse();
    assertThatThrownBy(() -> AppProfile.parse(null)).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> AppProfile.parse(" ")).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> AppProfile.parse("dev")).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void onlySlowSaltedHashesAreAccepted() {
    String strong = "{bcrypt}" + new BCryptPasswordEncoder(10).encode("x");
    String weak = "{bcrypt}" + new BCryptPasswordEncoder(4).encode("x");
    assertThatCode(() -> SecurityConfig.requireSlowHash(strong)).doesNotThrowAnyException();
    assertThatCode(() -> SecurityConfig.requireSlowHash("{pbkdf2}" + "ab".repeat(40)))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> SecurityConfig.requireSlowHash(weak)).hasMessageContaining("cost");
    assertThatThrownBy(() -> SecurityConfig.requireSlowHash("{noop}secret"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> SecurityConfig.requireSlowHash(strong.substring(8)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> SecurityConfig.requireSlowHash("{sha256}abc"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void organizerCredentialsMustBeConfigured() {
    SecurityConfig config = new SecurityConfig();
    AppProperties none =
        new AppProperties(null, null, null, null, null, null, null, null, null, null, null);
    assertThatThrownBy(() -> config.organizer(none)).hasMessageContaining("ORGANIZER");
    AppProperties blank =
        new AppProperties(
            null,
            null,
            null,
            null,
            null,
            new AppProperties.Organizer(" ", "x"),
            null,
            null,
            null,
            null,
            null);
    assertThatThrownBy(() -> config.organizer(blank)).hasMessageContaining("ORGANIZER");
    AppProperties ok =
        new AppProperties(
            null,
            null,
            null,
            null,
            null,
            new AppProperties.Organizer(
                "org", "{bcrypt}" + new BCryptPasswordEncoder(10).encode("pw")),
            null,
            null,
            null,
            null,
            null);
    assertThat(config.organizer(ok).loadUserByUsername("org").getAuthorities())
        .extracting(Object::toString)
        .containsExactly("ROLE_ORGANIZER");
  }

  @Test
  void problemBodiesContainOnlyPublicFields() {
    var body =
        Problems.body(
            org.springframework.http.HttpStatus.CONFLICT, "Conflict", java.util.List.of());
    assertThat(body).containsOnlyKeys("type", "title", "status");
    var withErrors =
        Problems.body(
            org.springframework.http.HttpStatus.BAD_REQUEST,
            "Validation failed",
            java.util.List.of(new FieldError("a", "REQUIRED", "m")));
    assertThat(withErrors).containsKey("errors");
    assertThat(ApiException.unavailable(new RuntimeException("db")).getCause()).isNotNull();
    assertThat(ApiException.conflict("c").status().value()).isEqualTo(409);
  }
}
