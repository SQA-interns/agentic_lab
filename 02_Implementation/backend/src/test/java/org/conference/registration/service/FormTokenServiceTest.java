package org.conference.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class FormTokenServiceTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-05-01T10:00:00Z"));
  private final FormTokenService service = new FormTokenService(TestProperties.defaults(), clock);

  @Test
  void tokenIsAcceptedAfterMinimumFillTime() {
    String token = service.issue();
    clock.advance(Duration.ofSeconds(5));
    FormTokenService.VerifiedToken verified = service.verify(token);
    assertThat(verified.nonce()).isNotBlank();
  }

  @Test
  void tokenSubmittedTooFastIsRejected() {
    String token = service.issue();
    clock.advance(Duration.ofSeconds(1));
    assertThatThrownBy(() -> service.verify(token))
        .isInstanceOf(SubmissionRejectedException.class)
        .hasMessageContaining("too fast");
  }

  @Test
  void expiredTokenIsRejected() {
    String token = service.issue();
    clock.advance(Duration.ofHours(3));
    assertThatThrownBy(() -> service.verify(token))
        .isInstanceOf(SubmissionRejectedException.class)
        .hasMessageContaining("expired");
  }

  @Test
  void tamperedTokenIsRejected() {
    String token = service.issue();
    clock.advance(Duration.ofSeconds(5));
    String tampered = token.substring(0, token.length() - 2) + "xx";
    assertThatThrownBy(() -> service.verify(tampered))
        .isInstanceOf(SubmissionRejectedException.class);
  }

  @Test
  void tokenFromOtherSecretIsRejected() {
    FormTokenService other =
        new FormTokenService(
            TestProperties.create("x", "another-secret-another-secret-0123456789", 3, 10), clock);
    String foreign = other.issue();
    clock.advance(Duration.ofSeconds(5));
    assertThatThrownBy(() -> service.verify(foreign))
        .isInstanceOf(SubmissionRejectedException.class);
  }

  @Test
  void malformedTokensAreRejected() {
    for (String token : new String[] {null, "", "abc", "a.b.c", "!!!.sig"}) {
      assertThatThrownBy(() -> service.verify(token))
          .isInstanceOf(SubmissionRejectedException.class);
    }
  }

  @Test
  void consumedTokenCannotBeReused() {
    String token = service.issue();
    clock.advance(Duration.ofSeconds(5));
    service.consume(service.verify(token));
    assertThatThrownBy(() -> service.verify(token))
        .isInstanceOf(SubmissionRejectedException.class)
        .hasMessageContaining("already used");
  }

  @Test
  void concurrentConsumeOfSameTokenFailsForSecondCaller() {
    String token = service.issue();
    clock.advance(Duration.ofSeconds(5));
    FormTokenService.VerifiedToken first = service.verify(token);
    FormTokenService.VerifiedToken second = service.verify(token);
    service.consume(first);
    assertThatThrownBy(() -> service.consume(second))
        .isInstanceOf(SubmissionRejectedException.class);
  }

  @Test
  void shortSecretIsRefusedAtStartup() {
    assertThatThrownBy(
            () -> new FormTokenService(TestProperties.create("x", "short", 3, 10), clock))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void missingSecretFallsBackToRandomSecret() {
    FormTokenService random = new FormTokenService(TestProperties.create("x", "", 3, 10), clock);
    String token = random.issue();
    clock.advance(Duration.ofSeconds(5));
    assertThat(random.verify(token)).isNotNull();
  }
}
