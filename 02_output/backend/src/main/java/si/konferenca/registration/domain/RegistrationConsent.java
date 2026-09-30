package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** A consent given, with the wording shown and the time it was given (SB-14). */
@Embeddable
public class RegistrationConsent {

  @Column(name = "consent_id", nullable = false, length = 64)
  private String consentId;

  @Column(name = "consent_text", nullable = false, length = 1000)
  private String consentText;

  @Column(name = "given_at", nullable = false)
  private Instant givenAt;

  protected RegistrationConsent() {}

  public RegistrationConsent(ConsentDefinition consent, Instant givenAt) {
    this.consentId = consent.id();
    this.consentText = consent.text();
    this.givenAt = givenAt;
  }

  public String consentId() {
    return consentId;
  }

  public String consentText() {
    return consentText;
  }

  public Instant givenAt() {
    return givenAt;
  }
}
