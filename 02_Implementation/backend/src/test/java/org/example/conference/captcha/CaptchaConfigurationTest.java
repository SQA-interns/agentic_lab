package org.example.conference.captcha;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** AC-X-01 / ST-05: deterministic captcha only in explicit non-production profiles; fail closed. */
class CaptchaConfigurationTest {

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(CaptchaProperties.class)
  @Import(CaptchaConfiguration.class)
  static class Config {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
          .withUserConfiguration(Config.class);

  private static void assertFailsWith(
      org.springframework.boot.test.context.assertj.AssertableApplicationContext context,
      String message) {
    assertThat(context).hasFailed();
    assertThat(context.getStartupFailure())
        .rootCause()
        .isInstanceOf(CaptchaConfigurationException.class)
        .hasMessageContaining(message);
  }

  @Test
  void productionDefaultRequiresRecaptchaKeys() {
    runner.run(ctx -> assertFailsWith(ctx, "required"));
    runner
        .withPropertyValues("app.captcha.site-key=site")
        .run(ctx -> assertFailsWith(ctx, "required"));
  }

  @Test
  void productionWithKeysUsesRecaptcha() {
    runner
        .withPropertyValues("app.captcha.site-key=site", "app.captcha.secret=secret")
        .run(
            ctx ->
                assertThat(ctx.getBean(CaptchaVerifier.class))
                    .isInstanceOf(RecaptchaVerifier.class));
  }

  @Test
  void testModeWithoutNonProductionProfileFails() {
    runner
        .withPropertyValues("app.captcha.mode=test", "app.captcha.test-token=t")
        .run(ctx -> assertFailsWith(ctx, "non-production"));
    runner
        .withPropertyValues(
            "spring.profiles.active=prod", "app.captcha.mode=test", "app.captcha.test-token=t")
        .run(ctx -> assertFailsWith(ctx, "non-production"));
  }

  @Test
  void prodCombinedWithLocalOrTestFails() {
    runner
        .withPropertyValues(
            "spring.profiles.active=prod,local",
            "app.captcha.site-key=site",
            "app.captcha.secret=secret")
        .run(ctx -> assertFailsWith(ctx, "cannot be combined"));
  }

  @Test
  void testModeRequiresToken() {
    runner
        .withPropertyValues("spring.profiles.active=local", "app.captcha.mode=test")
        .run(ctx -> assertFailsWith(ctx, "test-token"));
  }

  @Test
  void localProfileTestModeIsDeterministic() {
    runner
        .withPropertyValues(
            "spring.profiles.active=local", "app.captcha.mode=test", "app.captcha.test-token=ok")
        .run(
            ctx -> {
              CaptchaVerifier verifier = ctx.getBean(CaptchaVerifier.class);
              assertThat(verifier.mode()).isEqualTo(CaptchaMode.TEST);
              assertThat(verifier.verify("ok", null)).isTrue();
              assertThat(verifier.verify("ok ", null)).isFalse();
              assertThat(verifier.verify(null, null)).isFalse();
            });
  }
}
