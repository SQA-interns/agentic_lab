package si.konferenca.registration.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import si.konferenca.registration.domain.FieldError;

/**
 * Problem answers (RFC 9457) with fixed texts only: nothing of an exception, the configuration or
 * the request is ever copied into an answer (SB-07, ES-07).
 */
public final class Problems {

  private static final Map<String, String> MESSAGES =
      Map.ofEntries(
          Map.entry(FieldError.REQUIRED, "To polje je obvezno."),
          Map.entry(FieldError.TOO_LONG, "Vnos je predolg."),
          Map.entry(FieldError.INVALID_FORMAT, "Vnesite veljaven e-poštni naslov."),
          Map.entry(FieldError.INVALID_CHARACTERS, "Vnos vsebuje nedovoljene znake."),
          Map.entry(FieldError.INVALID_TYPE, "Vrednost ni veljavna."),
          Map.entry(FieldError.OPTION_NOT_SELECTABLE, "Izbrana možnost ni na voljo."),
          Map.entry(FieldError.OPTION_DUPLICATE, "Možnost je izbrana večkrat."),
          Map.entry(FieldError.TOO_MANY, "Izbranih je preveč možnosti."),
          Map.entry(FieldError.CONSENT_REQUIRED, "Za prijavo je potrebno soglasje."),
          Map.entry(FieldError.CAPTCHA_FAILED, "Potrdite, da niste robot."),
          Map.entry(FieldError.UNKNOWN_FIELD, "Polje ni del prijave."),
          Map.entry(FieldError.MALFORMED, "Prijave ni bilo mogoče prebrati."));

  private static final Map<Integer, String> TITLES =
      Map.of(
          400, "Neveljavna zahteva",
          401, "Potrebna je prijava organizatorja",
          403, "Dostop ni dovoljen",
          404, "Ni najdeno",
          405, "Metoda ni dovoljena",
          413, "Zahteva je prevelika",
          415, "Vrsta vsebine ni podprta",
          429, "Preveč zahtev",
          503, "Prijave nismo prejeli. Poskusite znova pozneje.");

  private static final String DEFAULT_TITLE = "Napaka";

  private Problems() {}

  /** The fixed body of a problem with the given status. */
  public static Map<String, Object> body(int status) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "about:blank");
    body.put("title", TITLES.getOrDefault(status, DEFAULT_TITLE));
    body.put("status", status);
    return body;
  }

  /** A problem answer with the given status. */
  public static ResponseEntity<Map<String, Object>> problem(HttpStatusCode status) {
    return ResponseEntity.status(status)
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(body(status.value()));
  }

  /** The answer to a rejected registration (schema ValidationProblem). */
  public static ResponseEntity<Map<String, Object>> validation(List<FieldError> errors) {
    Map<String, Object> body = body(400);
    body.put("title", "Prijava ni veljavna");
    body.put(
        "errors",
        errors.stream()
            .map(
                error ->
                    Map.of(
                        "field", error.field(),
                        "code", error.code(),
                        "message", MESSAGES.getOrDefault(error.code(), DEFAULT_TITLE)))
            .toList());
    return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
  }
}
