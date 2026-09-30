package lab.conference.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import lab.conference.TestCatalogs;
import lab.conference.notifications.NotificationService;
import lab.conference.platform.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class RegistrationServiceTest {

  private final RegistrationRepository repo = mock(RegistrationRepository.class);
  private final JsonStore store = mock(JsonStore.class);
  private final NotificationService notifications = mock(NotificationService.class);
  private final TransactionTemplate tx = mock(TransactionTemplate.class);
  private final CaptchaVerifier captcha = new StubCaptchaVerifier();
  private final RegistrationValidator validator =
      new RegistrationValidator(TestCatalogs.withConsent(true));
  private final RegistrationService service =
      new RegistrationService(
          validator,
          captcha,
          repo,
          store,
          notifications,
          tx,
          Clock.fixed(Instant.parse("2026-01-01T00:00:00.123456Z"), ZoneOffset.UTC),
          new OrganizerRecipients(List.of("o@example.test")));

  private static ObjectNode body(String token) {
    ObjectNode n = new ObjectMapper().createObjectNode();
    n.put("clientRequestId", UUID.randomUUID().toString());
    n.put("captchaToken", token);
    n.put("firstName", "Ana");
    n.put("lastName", "K");
    n.put("email", "a@example.test");
    n.put("organization", "O");
    n.put("consentGiven", true);
    return n;
  }

  @SuppressWarnings("unchecked")
  private void runTransactions() {
    org.mockito.Mockito.doAnswer(
            inv -> {
              ((Consumer<TransactionStatus>) inv.getArgument(0)).accept(null);
              return null;
            })
        .when(tx)
        .executeWithoutResult(any());
  }

  @Test
  void acceptsAfterPublishingAndCommitting() throws Exception {
    runTransactions();
    AcceptanceResult r =
        service.accept(FormType.EXTERNAL, body(StubCaptchaVerifier.VALID_TOKEN), "1.2.3.4");
    assertThat(r.replay()).isFalse();
    assertThat(r.acceptedAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00.123Z"));
    verify(store).publish(any(), any());
    verify(repo).saveAndFlush(any());
    verify(notifications).enqueue(any());
    verify(store).settled(r.registrationId());
  }

  @Test
  void replayReturnsOriginalWithoutCaptchaOrWrites() throws Exception {
    ObjectNode b = body("already-used-token");
    ValidatedRegistration v = validator.validate(FormType.EXTERNAL, b);
    RegistrationEntity existing = new RegistrationEntity(UUID.randomUUID(), Instant.EPOCH, v, "h");
    when(repo.findByClientRequestId(v.clientRequestId())).thenReturn(Optional.of(existing));
    AcceptanceResult r = service.accept(FormType.EXTERNAL, b, "x");
    assertThat(r.replay()).isTrue();
    assertThat(r.registrationId()).isEqualTo(existing.id());
    verify(store, never()).publish(any(), any());
  }

  @Test
  void differentContentWithSameRequestIdIsConflict() {
    ObjectNode b = body(StubCaptchaVerifier.VALID_TOKEN);
    ValidatedRegistration v = validator.validate(FormType.EXTERNAL, b);
    when(repo.findByClientRequestId(v.clientRequestId()))
        .thenReturn(Optional.of(new RegistrationEntity(UUID.randomUUID(), Instant.EPOCH, v, "h")));
    b.put("firstName", "Other");
    assertThatThrownBy(() -> service.accept(FormType.EXTERNAL, b, "x"))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).status().value())
        .isEqualTo(409);
  }

  @Test
  void failedCaptchaWritesNothing() throws Exception {
    assertThatThrownBy(() -> service.accept(FormType.EXTERNAL, body("bad"), "x"))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).errors().get(0).code())
        .isEqualTo("CAPTCHA_FAILED");
    verify(store, never()).publish(any(), any());
  }

  @Test
  void jsonFailureIsUnavailableAndSkipsDatabase() throws Exception {
    doThrow(new IOException("disk")).when(store).publish(any(), any());
    assertThatThrownBy(
            () -> service.accept(FormType.EXTERNAL, body(StubCaptchaVerifier.VALID_TOKEN), "x"))
        .isInstanceOf(ApiException.class)
        .extracting(e -> ((ApiException) e).status().value())
        .isEqualTo(503);
    verify(repo, never()).saveAndFlush(any());
  }

  @Test
  void databaseFailureDiscardsTheJson() {
    doThrow(new CannotCreateTransactionException("down")).when(tx).executeWithoutResult(any());
    assertThatThrownBy(
            () -> service.accept(FormType.EXTERNAL, body(StubCaptchaVerifier.VALID_TOKEN), "x"))
        .extracting(e -> ((ApiException) e).status().value())
        .isEqualTo(503);
    verify(store).discard(any());
  }

  @Test
  void databaseDownDuringReplayCheckIsUnavailable() {
    when(repo.findByClientRequestId(any())).thenThrow(new CannotCreateTransactionException("down"));
    assertThatThrownBy(
            () -> service.accept(FormType.EXTERNAL, body(StubCaptchaVerifier.VALID_TOKEN), "x"))
        .extracting(e -> ((ApiException) e).status().value())
        .isEqualTo(503);
  }

  @Test
  void concurrentDuplicateResolvesToTheWinner() throws Exception {
    ObjectNode b = body(StubCaptchaVerifier.VALID_TOKEN);
    ValidatedRegistration v = validator.validate(FormType.EXTERNAL, b);
    RegistrationEntity winner = new RegistrationEntity(UUID.randomUUID(), Instant.EPOCH, v, "h");
    when(repo.findByClientRequestId(v.clientRequestId()))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(winner));
    doThrow(new DataIntegrityViolationException("dup")).when(tx).executeWithoutResult(any());
    AcceptanceResult r = service.accept(FormType.EXTERNAL, b, "x");
    assertThat(r.registrationId()).isEqualTo(winner.id());
    assertThat(r.replay()).isTrue();
    verify(store).discard(any());
  }

  @Test
  void integrityViolationWithoutWinnerIsUnavailable() {
    doThrow(new DataIntegrityViolationException("other")).when(tx).executeWithoutResult(any());
    assertThatThrownBy(
            () -> service.accept(FormType.EXTERNAL, body(StubCaptchaVerifier.VALID_TOKEN), "x"))
        .extracting(e -> ((ApiException) e).status().value())
        .isEqualTo(503);
  }

  @Test
  void captchaGuardsFollowTheProfile() {
    assertThat(RegistrationConfig.stub(lab.conference.platform.AppProfile.LOCAL).mode())
        .isEqualTo("stub");
    assertThatThrownBy(() -> RegistrationConfig.stub(lab.conference.platform.AppProfile.PRODUCTION))
        .hasMessageContaining("production");
    assertThatThrownBy(
            () ->
                RegistrationConfig.recaptcha(
                    new lab.conference.platform.AppProperties.Captcha("recaptcha", "", "s")))
        .hasMessageContaining("RECAPTCHA");
    CaptchaVerifier real =
        RegistrationConfig.recaptcha(
            new lab.conference.platform.AppProperties.Captcha("recaptcha", " site ", "sec"));
    assertThat(real.mode()).isEqualTo("recaptcha");
    assertThat(real.siteKey()).isEqualTo("site");
    RegistrationConfig config = new RegistrationConfig();
    var props =
        new lab.conference.platform.AppProperties(
            null,
            null,
            null,
            new lab.conference.platform.AppProperties.Captcha("other", null, null),
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    assertThatThrownBy(() -> config.captchaVerifier(props, lab.conference.platform.AppProfile.TEST))
        .hasMessageContaining("CAPTCHA_MODE");
    assertThat(captcha.verify("", "x")).isFalse();
  }
}
