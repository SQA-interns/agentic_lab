package si.konferenca.registration.application;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.Consent;
import si.konferenca.registration.domain.OptionCatalog;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;

/** Shared test data for unit tests. */
public final class Fixtures {

  public static final Consent CONSENT = new Consent("data-processing", "I agree.");
  public static final ConferenceOption WORKSHOP =
      new ConferenceOption(
          "ws-ai",
          "AI workshop",
          OptionCategory.WORKSHOP,
          true,
          EnumSet.allOf(RegistrationType.class));
  public static final ConferenceOption GALA =
      new ConferenceOption(
          "ev-gala", "Gala dinner", OptionCategory.EVENT, true, Set.of(RegistrationType.EXTERNAL));
  public static final ConferenceOption OLD_MEAL =
      new ConferenceOption(
          "meal-old",
          "Old meal",
          OptionCategory.MEAL,
          false,
          EnumSet.allOf(RegistrationType.class));
  public static final OptionCatalog CATALOG =
      new OptionCatalog(CONSENT, List.of(WORKSHOP, GALA, OLD_MEAL));

  private Fixtures() {}

  public static RegistrationCommand external() {
    return new RegistrationCommand(
        RegistrationType.EXTERNAL,
        "Ana",
        "Novak",
        "ana@example.si",
        "IJS",
        null,
        null,
        null,
        List.of("ws-ai"),
        true,
        "token");
  }

  public static RegistrationCommand student() {
    return new RegistrationCommand(
        RegistrationType.STUDENT,
        "Luka",
        "Kranjc",
        "luka@example.si",
        null,
        "UM",
        "Informatika",
        "931",
        List.of(),
        true,
        "token");
  }

  public static Registration registration() {
    return new Registration(
        UUID.fromString("0b9f7a52-5c1e-4c55-9d1e-3f1f1f6a2b10"),
        RegistrationType.EXTERNAL,
        "Ana",
        "Novak",
        "Ana@Example.si",
        "IJS",
        null,
        null,
        null,
        List.of(WORKSHOP, GALA),
        CONSENT,
        Instant.parse("2026-10-06T18:00:00.123Z"));
  }
}
