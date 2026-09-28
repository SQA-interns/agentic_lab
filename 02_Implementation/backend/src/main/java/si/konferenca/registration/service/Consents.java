package si.konferenca.registration.service;

import java.util.List;

/** The consents presented on both registration forms (FORM_SCHEMA — Consent). */
public final class Consents {

  public static final String PRIVACY = "privacy";

  public static final List<ConsentDefinition> ALL =
      List.of(
          new ConsentDefinition(
              PRIVACY,
              "I agree that my personal data is processed for the purpose of organising the"
                  + " conference and managing my registration.",
              true));

  private Consents() {}

  /** A consent shown on the forms. */
  public record ConsentDefinition(String id, String text, boolean mandatory) {}
}
