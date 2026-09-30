package si.konferenca.registration.application;

/** Port: sends one plain-text email (docs/02_contracts/emails.md). */
public interface Mailer {

  /**
   * Sends the message.
   *
   * @throws MailDeliveryException when the mail server does not accept it
   */
  void send(OutgoingMail mail);
}
