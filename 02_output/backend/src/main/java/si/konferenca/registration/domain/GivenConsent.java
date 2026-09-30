package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** A consent the participant gave, with the time it was given (SB-14). */
@Embeddable
public class GivenConsent {

  @Column(name = "consent_id", nullable = false, length = 64)
  private String consentId;

  @Column(name = "given_at", nullable = false)
  private Instant givenAt;

  protected GivenConsent() {}

  public GivenConsent(String consentId, Instant givenAt) {
    this.consentId = consentId;
    this.givenAt = givenAt;
  }

  public String getConsentId() {
    return consentId;
  }

  public Instant getGivenAt() {
    return givenAt;
  }
}
