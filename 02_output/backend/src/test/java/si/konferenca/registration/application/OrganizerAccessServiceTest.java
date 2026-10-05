package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class OrganizerAccessServiceTest {

  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-10-05T10:00:00Z"));
  private final Clock clock =
      new Clock() {
        @Override
        public ZoneId getZone() {
          return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
          return this;
        }

        @Override
        public Instant instant() {
          return now.get();
        }
      };
  private final OrganizerAccessService service =
      new OrganizerAccessService("organizer", "correct horse battery", true, clock);

  @Test
  void issuesARandomTokenValidForFifteenMinutes() {
    var first = service.issue("organizer", "correct horse battery").orElseThrow();
    var second = service.issue("organizer", "correct horse battery").orElseThrow();

    assertThat(first.value()).hasSize(43).isNotEqualTo(second.value());
    assertThat(first.expiresAt()).isEqualTo(Instant.parse("2026-10-05T10:15:00Z"));
    assertThat(service.isValid(first.value())).isTrue();
    now.set(Instant.parse("2026-10-05T10:14:59Z"));
    assertThat(service.isValid(first.value())).isTrue();
    now.set(Instant.parse("2026-10-05T10:15:00Z"));
    assertThat(service.isValid(first.value())).isFalse();
  }

  @Test
  void wrongCredentialsGetNoToken() {
    assertThat(service.issue("organizer", "wrong")).isEmpty();
    assertThat(service.issue("Organizer", "correct horse battery")).isEmpty();
    assertThat(service.issue(null, null)).isEmpty();
  }

  @Test
  void unknownBlankOrNullTokensAreInvalid() {
    assertThat(service.isValid(null)).isFalse();
    assertThat(service.isValid(" ")).isFalse();
    assertThat(service.isValid("forged")).isFalse();
  }

  @Test
  void expiredTokensArePurgedWhenANewOneIsIssued() {
    var old = service.issue("organizer", "correct horse battery").orElseThrow();
    now.set(Instant.parse("2026-10-05T11:00:00Z"));
    var fresh = service.issue("organizer", "correct horse battery").orElseThrow();
    now.set(Instant.parse("2026-10-05T10:05:00Z"));

    assertThat(service.isValid(old.value())).isFalse();
    assertThat(service.isValid(fresh.value())).isTrue();
  }

  @Test
  void reportsTheHttpsPolicy() {
    assertThat(service.httpsOnly()).isTrue();
    assertThat(new OrganizerAccessService("u", "p", false, clock).httpsOnly()).isFalse();
  }
}
