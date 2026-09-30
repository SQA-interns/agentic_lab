package lab.conference.registration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lab.conference.platform.ApiException;
import lab.conference.platform.FieldError;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** POST /api/registrations/{external|student} (openapi.yaml). */
@RestController
public class RegistrationController {

  private static final ObjectMapper JSON =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  private final RegistrationService service;

  public RegistrationController(RegistrationService service) {
    this.service = service;
  }

  @PostMapping(
      path = "/api/registrations/{form:external|student}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, Object>> register(
      @PathVariable("form") String form, @RequestBody byte[] body, HttpServletRequest request) {
    JsonNode json;
    try {
      json = JSON.readTree(body);
    } catch (IOException e) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "Validation failed",
          List.of(
              new FieldError("body", "MALFORMED_REQUEST", "The request body is not valid JSON.")),
          e);
    }
    AcceptanceResult r = service.accept(FormType.fromKey(form), json, request.getRemoteAddr());
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("registrationId", r.registrationId().toString());
    response.put("clientRequestId", r.clientRequestId().toString());
    response.put("formType", r.formType().key());
    response.put("status", "ACCEPTED");
    response.put("acceptedAt", r.acceptedAt().toString());
    return ResponseEntity.status(r.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
  }
}
