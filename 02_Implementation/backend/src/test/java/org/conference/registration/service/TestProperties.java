package org.conference.registration.service;

import java.util.List;
import org.conference.registration.config.AppProperties;

/** Builds {@link AppProperties} for unit tests. */
final class TestProperties {

  static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

  private TestProperties() {}

  static AppProperties create(
      String optionsFile, String secret, long minFillSeconds, int registrationLimit) {
    return new AppProperties(
        "Test Conference",
        new AppProperties.Options(optionsFile),
        new AppProperties.Backup("target/test-backups"),
        new AppProperties.Mail(
            "registration@conference.test",
            List.of("org1@conference.test", "org2@conference.test")),
        new AppProperties.Admin("organizer", "organizer-password"),
        new AppProperties.Antibot(secret, minFillSeconds, 7200),
        new AppProperties.RateLimit(600, registrationLimit, 5, 3));
  }

  static AppProperties defaults() {
    return create("classpath:conference-options.json", SECRET, 3, 10);
  }

  static AppProperties withOptionsFile(String file) {
    return create(file, SECRET, 3, 10);
  }
}
