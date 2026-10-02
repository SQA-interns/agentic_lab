package si.konferenca.registration.adapter.out.mail;

import java.util.Map;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.Registration.SelectedOption;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.TextField;

/**
 * The plain-text bodies of the emails (email-messages.schema.json). Lines are "Label: value"; the
 * values are stored values, which never contain line breaks, so no value can add a line (SR-05).
 */
final class MailTexts {

  private static final Map<TextField, String> LABELS =
      Map.of(
          TextField.FIRST_NAME, "Ime",
          TextField.LAST_NAME, "Priimek",
          TextField.EMAIL, "E-pošta",
          TextField.ORGANIZATION, "Organizacija / ustanova",
          TextField.STUDY_INSTITUTION, "Izobraževalna ustanova",
          TextField.STUDY_PROGRAMME, "Študijski program",
          TextField.STUDENT_ID, "Vpisna številka");

  private static final Map<RegistrationType, String> TYPE_NAMES =
      Map.of(RegistrationType.EXTERNAL, "Zunanji udeleženec", RegistrationType.STUDENT, "Študent");

  private MailTexts() {}

  static String participantConfirmation(Registration registration, String conferenceName) {
    StringBuilder text = new StringBuilder(512);
    text.append("Spoštovani,\n\nvašo prijavo na dogodek ")
        .append(conferenceName)
        .append(" smo prejeli.\n\n");
    line(text, "Številka prijave", registration.id().toString());
    submittedData(text, registration);
    return text.toString();
  }

  static String organizerNotification(Registration registration) {
    StringBuilder text = new StringBuilder(512);
    text.append("Nova prijava.\n\n");
    line(text, "Številka prijave", registration.id().toString());
    line(text, "Sprejeto", registration.acceptedAt().toString());
    submittedData(text, registration);
    text.append('\n');
    line(text, "Soglasje dano", registration.consent().givenAt().toString());
    return text.toString();
  }

  private static void submittedData(StringBuilder text, Registration registration) {
    line(text, "Vrsta prijave", TYPE_NAMES.get(registration.type()));
    for (TextField field : registration.type().fields()) {
      line(text, LABELS.get(field), registration.value(field));
    }
    text.append("\nIzbrane možnosti:\n");
    if (registration.options().isEmpty()) {
      text.append("- brez\n");
    }
    for (SelectedOption option : registration.options()) {
      text.append("- ").append(option.name()).append('\n');
    }
  }

  private static void line(StringBuilder text, String label, String value) {
    text.append(label).append(": ").append(value).append('\n');
  }
}
