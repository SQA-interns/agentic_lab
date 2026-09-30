package lab.conference.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lab.conference.TestCatalogs;
import lab.conference.notifications.Hashes;
import lab.conference.platform.ApiException;
import lab.conference.platform.AppProfile;
import lab.conference.platform.AppProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

/** Targets surviving mutants in validation and persistence code (phase 6 fix loop). */
class EdgeCasesTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private final RegistrationValidator validator =
      new RegistrationValidator(TestCatalogs.withConsent(true));

  private static ObjectNode body() {
    ObjectNode n = JSON.createObjectNode();
    n.put("clientRequestId", "3f1c7c9e-0d5e-4d38-9d53-2a2c8f0e4b11");
    n.put("captchaToken", "t");
    n.put("firstName", "A");
    n.put("lastName", "B");
    n.put("email", "a@example.test");
    n.put("organization", "O");
    n.put("consentGiven", true);
    return n;
  }

  @Test
  void missingNullAndEmptySelectionsHaveTheSameFingerprint() {
    ObjectNode missing = body();
    ObjectNode nulled = body();
    nulled.putNull("selections");
    ObjectNode empty = body();
    ObjectNode groups = empty.putObject("selections");
    groups.putArray("workshops");
    groups.putNull("events");
    String f = validator.validate(FormType.EXTERNAL, missing).fingerprint();
    assertThat(validator.validate(FormType.EXTERNAL, nulled).fingerprint()).isEqualTo(f);
    assertThat(validator.validate(FormType.EXTERNAL, empty).fingerprint()).isEqualTo(f);
    assertThat(validator.validate(FormType.EXTERNAL, missing).selections()).hasSize(4);
  }

  @Test
  void fiftyOptionsAreNotTooMany() {
    ObjectNode b = body();
    var arr = b.putObject("selections").putArray("workshops");
    for (int i = 0; i < 50; i++) {
      arr.add("x" + i);
    }
    try {
      validator.validate(FormType.EXTERNAL, b);
    } catch (ApiException e) {
      assertThat(e.errors())
          .extracting("code")
          .doesNotContain("TOO_MANY_OPTIONS")
          .contains("UNKNOWN_OPTION");
      assertThat(e.errors()).extracting("message").contains("A selected option is not available.");
      return;
    }
    throw new AssertionError("unknown options must fail");
  }

  @Test
  void optionErrorMessagesAreSpecific() {
    ObjectNode b = body();
    b.putObject("selections").putArray("workshops").add("ws-a").add("ws-a").add(1);
    assertThatThrownBy(() -> validator.validate(FormType.EXTERNAL, b))
        .isInstanceOf(ApiException.class)
        .satisfies(
            e ->
                assertThat(((ApiException) e).errors())
                    .extracting("message")
                    .contains(
                        "An option was selected more than once.",
                        "Selections must be option IDs."));
  }

  @Test
  void sha256MatchesKnownVector() {
    assertThat(Hashes.sha256("abc".getBytes()))
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  void entityCopiesTheValidatedRegistration() {
    ObjectNode b = body();
    b.putObject("selections").putArray("workshops").add("ws-a");
    ValidatedRegistration v = validator.validate(FormType.EXTERNAL, b);
    UUID id = UUID.randomUUID();
    Instant at = Instant.parse("2026-01-01T00:00:00Z");
    RegistrationEntity e = new RegistrationEntity(id, at, v, "h".repeat(64));
    assertThat(e.id()).isEqualTo(id);
    assertThat(e.acceptedAt()).isEqualTo(at);
    assertThat(e.clientRequestId()).isEqualTo(v.clientRequestId());
    assertThat(e.jsonSha256()).isEqualTo("h".repeat(64));
    assertThat(e.consentGiven()).isTrue();
    assertThat(e.firstName()).isEqualTo("A");
    assertThat(e.lastName()).isEqualTo("B");
    assertThat(e.email()).isEqualTo("a@example.test");
    assertThat(e.organization()).isEqualTo("O");
    assertThat(e.studentId()).isNull();
    assertThat(e.studyInstitution()).isNull();
    assertThat(e.studyProgramme()).isNull();
    assertThat(e.selections())
        .singleElement()
        .satisfies(
            s -> {
              assertThat(s.groupId()).isEqualTo("workshops");
              assertThat(s.optionId()).isEqualTo("ws-a");
              assertThat(s.optionName()).isEqualTo("Workshop A");
            });
    RegistrationValidator none = new RegistrationValidator(TestCatalogs.withoutConsent());
    ObjectNode nb = body();
    nb.remove("consentGiven");
    assertThat(
            new RegistrationEntity(id, at, none.validate(FormType.EXTERNAL, nb), "h")
                .consentGiven())
        .isNull();
  }

  @Test
  void missingCaptchaConfigurationIsRefused() {
    RegistrationConfig config = new RegistrationConfig();
    AppProperties noCaptcha =
        new AppProperties(null, null, null, null, null, null, null, null, null, null, null);
    assertThatThrownBy(() -> config.captchaVerifier(noCaptcha, AppProfile.TEST))
        .hasMessageContaining("CAPTCHA_MODE");
    AppProperties noMode =
        new AppProperties(
            null,
            null,
            null,
            new AppProperties.Captcha(null, null, null),
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThatThrownBy(() -> config.captchaVerifier(noMode, AppProfile.TEST))
        .hasMessageContaining("CAPTCHA_MODE");
    AppProperties stub =
        new AppProperties(
            null,
            null,
            null,
            new AppProperties.Captcha(" stub ", null, null),
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThat(config.captchaVerifier(stub, AppProfile.LOCAL))
        .isInstanceOf(StubCaptchaVerifier.class);
    assertThat(new StubCaptchaVerifier().siteKey()).isNull();
  }

  @Test
  void startupReconciliationQuarantinesOrphans(@TempDir Path root) throws Exception {
    JsonStore store = new JsonStore(root);
    UUID id = UUID.randomUUID();
    store.publish(id, "{}".getBytes());
    store.settled(id);
    Files.setLastModifiedTime(store.file(id), FileTime.from(Instant.now().minusSeconds(3600)));
    RegistrationRepository repo = Mockito.mock(RegistrationRepository.class);
    Mockito.when(repo.findAllInAcceptanceOrder()).thenReturn(List.of());
    new Reconciler(
            store,
            repo,
            Clock.systemUTC(),
            new AppProperties(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Duration.ofMinutes(5),
                Duration.ofMinutes(2)))
        .atStartup();
    assertThat(store.file(id)).doesNotExist();
    Mockito.verify(repo).findAllInAcceptanceOrder();
  }

  @Test
  void validatorRejectsUuidLookalikes() {
    ObjectNode b = body();
    b.put("clientRequestId", "3f1c7c9e0d5e4d389d532a2c8f0e4b11");
    assertThatThrownBy(() -> validator.validate(FormType.EXTERNAL, b))
        .isInstanceOf(ApiException.class);
    b.put("clientRequestId", "3F1C7C9E-0D5E-4D38-9D53-2A2C8F0E4B11");
    assertThat(validator.validate(FormType.EXTERNAL, b).clientRequestId()).isNotNull();
  }
}
