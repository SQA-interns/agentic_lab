package si.konferenca.registration.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.service.ErrorCode;
import si.konferenca.registration.service.RegistrationRejectedException;
import si.konferenca.registration.service.RegistrationRequest;

class RegistrationRequestParserTest {

  private static RegistrationRequest parse(String json) {
    return RegistrationRequestParser.parse(json.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void readsEveryPropertyAndTreatsNullAsAbsent() {
    RegistrationRequest request =
        parse(
            "{\"type\":\"STUDENT\",\"firstName\":\"Špela\",\"lastName\":\"Ž\",\"email\":\"e\","
                + "\"organization\":null,\"studyInstitution\":\"U\",\"studyProgramme\":\"P\","
                + "\"studentId\":\"1\",\"optionIds\":[\"a\",\"b\"],\"consentIds\":null,"
                + "\"antiAutomationToken\":\"t\"}");

    assertThat(request.type()).isEqualTo("STUDENT");
    assertThat(request.firstName()).isEqualTo("Špela");
    assertThat(request.organization()).isNull();
    assertThat(request.optionIds()).containsExactly("a", "b");
    assertThat(request.consentIds()).isNull();
    assertThat(request.antiAutomationToken()).isEqualTo("t");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "not json",
        "[]",
        "\"text\"",
        "{\"unknown\":1}",
        "{\"firstName\":1}",
        "{\"firstName\":{}}",
        "{\"optionIds\":\"a\"}",
        "{\"optionIds\":[1]}",
        "{\"firstName\":\"a\",\"firstName\":\"b\"}"
      })
  void refusesMalformedBodies(String json) {
    assertThatThrownBy(() -> parse(json))
        .isInstanceOfSatisfying(
            RegistrationRejectedException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.MALFORMED_REQUEST));
  }

  @Test
  void readsABodyUpToTheLimitAndRefusesALongerOne() throws Exception {
    byte[] body = new byte[100];

    assertThat(RegistrationController.readLimited(new ByteArrayInputStream(body), 100))
        .hasSize(100);
    assertThatThrownBy(() -> RegistrationController.readLimited(new ByteArrayInputStream(body), 99))
        .isInstanceOfSatisfying(
            RegistrationRejectedException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.PAYLOAD_TOO_LARGE));
  }

  @Test
  void rateLimiterAllowsTheLimitPerMinuteAndResetsInTheNextWindow() {
    AtomicLong now = new AtomicLong(Instant.parse("2026-10-06T12:00:10Z").toEpochMilli());
    Clock clock =
        new Clock() {
          @Override
          public ZoneOffset getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return Instant.ofEpochMilli(now.get());
          }
        };
    FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(clock);

    assertThat(limiter.acquire("a", 2)).isZero();
    assertThat(limiter.acquire("a", 2)).isZero();
    assertThat(limiter.acquire("a", 2)).isEqualTo(50);
    assertThat(limiter.acquire("b", 2)).as("other client").isZero();
    now.addAndGet(50_000);
    assertThat(limiter.acquire("a", 2)).as("next window").isZero();
  }
}
