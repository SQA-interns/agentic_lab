package si.konferenca.registration.acceptance.support;

import java.net.http.HttpHeaders;
import java.nio.charset.StandardCharsets;
import tools.jackson.databind.JsonNode;

/** An HTTP response as seen by a client. */
public record Response(int status, HttpHeaders headers, byte[] body) {

  public String text() {
    return new String(body, StandardCharsets.UTF_8);
  }

  public JsonNode json() {
    return Json.read(text());
  }

  public String contentType() {
    return headers.firstValue("Content-Type").orElse("");
  }

  /** True when a 400 problem lists the given field with the given code. */
  public boolean hasFieldError(String field, String code) {
    if (status != 400) {
      return false;
    }
    for (JsonNode error : json().path("errors")) {
      if (field.equals(error.path("field").asString())
          && code.equals(error.path("code").asString())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    return "HTTP " + status + " " + contentType() + " " + text();
  }
}
