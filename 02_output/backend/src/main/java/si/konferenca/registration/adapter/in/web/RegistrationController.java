package si.konferenca.registration.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import si.konferenca.registration.application.SubmitRegistration;
import si.konferenca.registration.domain.Registration;

/** The public registration operation (openapi.yaml, createRegistration). */
@RestController
public class RegistrationController {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationController.class);

  private final SubmitRegistration submitRegistration;
  private final int maxRequestBytes;
  private final RegistrationRequestParser parser = new RegistrationRequestParser();

  public RegistrationController(SubmitRegistration submitRegistration, RequestLimits limits) {
    this.submitRegistration = submitRegistration;
    this.maxRequestBytes = limits.maxRequestBytes();
  }

  /** The request-size limit of the registration operation (SR-03). */
  public record RequestLimits(int maxRequestBytes) {}

  /** Schema RegistrationAccepted. */
  public record RegistrationAcceptedResponse(UUID id, Instant acceptedAt) {}

  @PostMapping("/api/registrations")
  public ResponseEntity<RegistrationAcceptedResponse> create(HttpServletRequest request)
      throws IOException {
    requireJson(request);
    Registration registration = submitRegistration.submit(parser.parse(readBody(request)));
    LOG.info("registration {} accepted, type {}", registration.id(), registration.type());
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new RegistrationAcceptedResponse(registration.id(), registration.acceptedAt()));
  }

  private static void requireJson(HttpServletRequest request) {
    if (!isJson(request.getContentType())) {
      throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }
  }

  private static boolean isJson(String contentType) {
    if (contentType == null) {
      return false;
    }
    try {
      return MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType));
    } catch (InvalidMediaTypeException e) {
      return false;
    }
  }

  /** Reads at most the allowed number of bytes; a longer body is refused while reading. */
  private byte[] readBody(HttpServletRequest request) throws IOException {
    if (request.getContentLengthLong() > maxRequestBytes) {
      throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
    }
    byte[] body = request.getInputStream().readNBytes(maxRequestBytes + 1);
    if (body.length > maxRequestBytes) {
      throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE);
    }
    return body;
  }
}
