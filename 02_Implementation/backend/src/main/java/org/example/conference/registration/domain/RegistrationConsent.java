package org.example.conference.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A consent granted at submission, with the configured wording snapshot. */
@Entity
@Table(name = "registration_consent")
public class RegistrationConsent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "registration_id", nullable = false)
  private Registration registration;

  @Column(name = "consent_id", nullable = false)
  private String consentId;

  @Column(name = "consent_text", nullable = false)
  private String consentText;

  protected RegistrationConsent() {}

  RegistrationConsent(Registration registration, String consentId, String consentText) {
    this.registration = registration;
    this.consentId = consentId;
    this.consentText = consentText;
  }

  public String getConsentId() {
    return consentId;
  }

  public String getConsentText() {
    return consentText;
  }
}
