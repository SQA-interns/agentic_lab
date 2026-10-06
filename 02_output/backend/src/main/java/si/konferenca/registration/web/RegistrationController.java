package si.konferenca.registration.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.domain.Participant;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.service.ErrorCode;
import si.konferenca.registration.service.RegistrationCopy;
import si.konferenca.registration.service.RegistrationRejectedException;
import si.konferenca.registration.service.RegistrationService;

/** POST /api/registrations (US-001, US-002, US-004). */
@RestController
public class RegistrationController {

  private final RegistrationService registrations;
  private final int maxRequestBytes;

  public RegistrationController(RegistrationService registrations, AppProperties app) {
    this.registrations = registrations;
    this.maxRequestBytes = app.maxRequestBytes();
  }

  @PostMapping(path = "/api/registrations", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RegistrationAccepted> register(HttpServletRequest request)
      throws IOException {
    byte[] body = readLimited(request.getInputStream(), maxRequestBytes);
    RegistrationService.Accepted accepted =
        registrations.register(RegistrationRequestParser.parse(body), request.getRemoteAddr());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(RegistrationAccepted.of(accepted.registration()));
  }

  /** Reads at most limit bytes; a longer body is PAYLOAD_TOO_LARGE (SR-03). */
  static byte[] readLimited(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    int read;
    while ((read = in.read(buffer)) != -1) {
      if (out.size() + read > limit) {
        throw new RegistrationRejectedException(ErrorCode.PAYLOAD_TOO_LARGE);
      }
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  /** `RegistrationAccepted` of openapi.yaml. */
  public record RegistrationAccepted(
      String id,
      String type,
      String firstName,
      String lastName,
      String email,
      List<OptionBody> options,
      String registeredAt) {

    public RegistrationAccepted {
      options = List.copyOf(options);
    }

    static RegistrationAccepted of(Registration registration) {
      Participant p = registration.participant();
      return new RegistrationAccepted(
          registration.id().toString(),
          registration.type().name(),
          p.firstName(),
          p.lastName(),
          p.email(),
          registration.options().stream()
              .map(o -> new OptionBody(o.optionId(), o.displayName(), o.category().name()))
              .toList(),
          RegistrationCopy.timestamp(registration.registeredAt()));
    }
  }

  /** A selected option. */
  public record OptionBody(String id, String displayName, String category) {}
}
