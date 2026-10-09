package si.konferenca.registration.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** A given consent as stored (SB-14). */
@Embeddable
public class ConsentColumns {

  @Column(name = "consent_id", nullable = false, length = 63)
  private String consentId;

  @Column(name = "consent_text", nullable = false, length = 2000)
  private String consentText;

  @Column(name = "given_at", nullable = false)
  private Instant givenAt;

  protected ConsentColumns() {}

  ConsentColumns(String consentId, String consentText, Instant givenAt) {
    this.consentId = consentId;
    this.consentText = consentText;
    this.givenAt = givenAt;
  }

  String consentId() {
    return consentId;
  }

  String consentText() {
    return consentText;
  }

  Instant givenAt() {
    return givenAt;
  }
}
