package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** A consent given, with the wording shown and the time given (registration_consent, SB-14). */
@Embeddable
public class GivenConsent {

  @Column(name = "consent_id", nullable = false, length = 64)
  private String consentId;

  @Column(name = "consent_text", nullable = false, columnDefinition = "text")
  private String consentText;

  @Column(name = "given_at", nullable = false)
  private Instant givenAt;

  protected GivenConsent() {}

  public GivenConsent(String consentId, String consentText, Instant givenAt) {
    this.consentId = consentId;
    this.consentText = consentText;
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
