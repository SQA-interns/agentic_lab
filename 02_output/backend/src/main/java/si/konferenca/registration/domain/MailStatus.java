package si.konferenca.registration.domain;

/** Delivery state of one notification email (D-11). */
public enum MailStatus {
  PENDING,
  SENT,
  FAILED,
  ABANDONED;

  public boolean needsSending() {
    return this == PENDING || this == FAILED;
  }
}
