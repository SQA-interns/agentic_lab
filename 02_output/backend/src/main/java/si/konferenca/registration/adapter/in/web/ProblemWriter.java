package si.konferenca.registration.adapter.in.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/** Writes a problem answer from a servlet filter, where no controller advice applies. */
public final class ProblemWriter {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ProblemWriter() {}

  /** Sets the status and writes the fixed problem body for it. */
  public static void write(HttpServletResponse response, int status) throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getOutputStream().write(JSON.writeValueAsBytes(Problems.body(status)));
  }
}
