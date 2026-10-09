package si.konferenca.registration.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared domain fixtures for unit tests. */
public final class Fixtures {

  public static final Instant NOW = Instant.parse("2026-10-09T10:00:00Z");
  public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private Fixtures() {}

  public static ConferenceCatalogue catalogue() {
    Set<RegistrationType> both = Set.of(RegistrationType.EXTERNAL, RegistrationType.STUDENT);
    return new ConferenceCatalogue(
        List.of(
            new ConferenceOption("ws-a", "Workshop A", Category.WORKSHOP, true, both),
            new ConferenceOption("ws-b", "Workshop B", Category.WORKSHOP, true, both),
            new ConferenceOption("ws-c", "Workshop C", Category.WORKSHOP, true, both),
            new ConferenceOption(
                "ws-ext",
                "External only",
                Category.WORKSHOP,
                true,
                Set.of(RegistrationType.EXTERNAL)),
            new ConferenceOption("ws-old", "Old", Category.WORKSHOP, false, both),
            new ConferenceOption("ev-a", "Event A", Category.EVENT, true, both),
            new ConferenceOption("ev-b", "Event B", Category.EVENT, true, both),
            new ConferenceOption("meal-a", "Meal A", Category.MEAL, true, both)),
        Map.of(Category.WORKSHOP, 2),
        List.of(
            new Consent("privacy", "I agree to processing.", true),
            new Consent("photo", "Photos are fine.", false)));
  }

  public static Map<Field, String> externalValues() {
    Map<Field, String> values = new EnumMap<>(Field.class);
    values.put(Field.FIRST_NAME, "Ana");
    values.put(Field.LAST_NAME, "Novak");
    values.put(Field.EMAIL, "ana@example.si");
    values.put(Field.ORGANIZATION, "IJS");
    return values;
  }

  public static Map<Field, String> studentValues() {
    Map<Field, String> values = new EnumMap<>(Field.class);
    values.put(Field.FIRST_NAME, "Luka");
    values.put(Field.LAST_NAME, "Kranjc");
    values.put(Field.EMAIL, "luka@example.si");
    values.put(Field.STUDY_INSTITUTION, "UM");
    values.put(Field.STUDY_PROGRAMME, "Informatika");
    values.put(Field.STUDENT_ID, "E1");
    return values;
  }

  public static Submission external(Map<Field, String> values, List<String> options) {
    return new Submission(RegistrationType.EXTERNAL, values, options, List.of("privacy"));
  }

  public static Registration registration(RegistrationType type) {
    Map<Field, String> values =
        type == RegistrationType.EXTERNAL ? externalValues() : studentValues();
    Submission submission =
        new Submission(type, values, List.of("ws-a", "ev-a"), List.of("privacy"));
    return new RegistrationValidator(catalogue(), CLOCK).validate(submission).registration();
  }
}
