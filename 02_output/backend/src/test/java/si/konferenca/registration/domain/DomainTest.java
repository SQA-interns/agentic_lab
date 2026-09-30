package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DomainTest {

  @ParameterizedTest
  @ValueSource(strings = {"a@b.si", "ana.novak+tag@sub.example.co.uk", "č@š.si"})
  void acceptsValidEmails(String email) {
    assertThat(EmailAddresses.isValid(email)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "a", "a@b", "a@b.", "a b@c.si", "a@b .si", "@b.si"})
  void rejectsInvalidEmails(String email) {
    assertThat(EmailAddresses.isValid(email)).isFalse();
  }

  @Test
  void emailLengthLimitIsInclusive() {
    String local = "a".repeat(EmailAddresses.MAX_LENGTH - "@b.si".length());

    assertThat(EmailAddresses.isValid(local + "@b.si")).isTrue();
    assertThat(EmailAddresses.isValid("a" + local + "@b.si")).isFalse();
    assertThat(EmailAddresses.isValid(null)).isFalse();
  }

  @Test
  void categoriesMapToTheirCodes() {
    assertThat(OptionCategory.fromCode("workshop")).contains(OptionCategory.WORKSHOP);
    assertThat(OptionCategory.fromCode("event")).contains(OptionCategory.EVENT);
    assertThat(OptionCategory.fromCode("meal")).contains(OptionCategory.MEAL);
    assertThat(OptionCategory.fromCode("other")).contains(OptionCategory.OTHER);
    assertThat(OptionCategory.fromCode("Workshop")).isEmpty();
    assertThat(OptionCategory.MEAL.code()).isEqualTo("meal");
  }

  @Test
  void registrationIsNewUntilStored() {
    Instant at = Instant.parse("2026-09-30T10:00:00Z");
    Registration r =
        new Registration(
            UUID.randomUUID(),
            at,
            new Registration.Details(
                RegistrationType.EXTERNAL, "A", "B", "a@b.si", "Org", null, null, null),
            List.of(new SelectedOption("ws", "W", "workshop")),
            List.of(new GivenConsent("privacy", at)));

    assertThat(r.isNew()).isTrue();
    r.markStored();
    assertThat(r.isNew()).isFalse();
    assertThat(r.getOptions()).hasSize(1);
    assertThat(r.getConsents().get(0).getConsentId()).isEqualTo("privacy");
    assertThat(r.getOrganization()).isEqualTo("Org");
    assertThat(r.getSubmittedAt()).isEqualTo(at);
  }
}
