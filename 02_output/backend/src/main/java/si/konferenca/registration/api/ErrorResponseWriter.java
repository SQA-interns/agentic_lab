package si.konferenca.registration.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/** Writes an {@link ErrorResponse} from filters and security handlers, outside Spring MVC. */
public final class ErrorResponseWriter {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ErrorResponseWriter() {}

  public static void write(HttpServletResponse response, int status, ErrorResponse body)
      throws IOException {
    response.setStatus(status);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getOutputStream().write(JSON.writeValueAsBytes(body));
  }
}
