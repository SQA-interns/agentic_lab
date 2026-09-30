package si.konferenca.registration.infrastructure;

import java.util.List;
import si.konferenca.registration.settings.AppProperties;

/** AppProperties for unit tests. */
final class TestProperties {

  private TestProperties() {}

  static AppProperties with(String optionsFile, String jsonDir) {
    return new AppProperties(
        "Konferenca",
        new AppProperties.Mail("from@conference.test"),
        optionsFile,
        jsonDir,
        new AppProperties.Recaptcha(true, "", "", "http://localhost/verify"),
        new AppProperties.Organizer(
            "org", "secret-password-123", List.of("a@org.test", " b@org.test"), true),
        new AppProperties.Cors(List.of()),
        new AppProperties.RateLimit(10, 10, 120),
        16384);
  }
}
