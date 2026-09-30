package si.konferenca.registration.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.json.JsonMapper;

/** Error bodies written outside controllers (filters, security handlers). */
public final class ErrorResponses {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private ErrorResponses() {}

  public static void write(HttpServletResponse response, ErrorBody body) throws IOException {
    response.setStatus(body.status());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response.getOutputStream().write(MAPPER.writeValueAsBytes(body));
  }

  static ErrorBody payloadTooLargeBody() {
    return ErrorBody.of(413, "payload_too_large", "The request is too large.");
  }

  static ResponseEntity<ErrorBody> payloadTooLarge() {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(payloadTooLargeBody());
  }
}
