package si.konferenca.registration.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import si.konferenca.registration.IntegrationTestBase;
import si.konferenca.registration.RegistrationJson;

@TestPropertySource(properties = {"app.rate-limit.max-requests=2", "app.rate-limit.window=PT1H"})
class RateLimitIntegrationTest extends IntegrationTestBase {

  private ResultActions submit(String remoteAddr) throws Exception {
    return mvc.perform(
        post("/api/registrations")
            .with(
                request -> {
                  request.setRemoteAddr(remoteAddr);
                  return request;
                })
            .contentType(MediaType.APPLICATION_JSON)
            .characterEncoding(StandardCharsets.UTF_8)
            .content(RegistrationJson.external().build()));
  }

  @Test
  void excessiveSubmissionsFromOneClientAreRejected() throws Exception {
    submit("203.0.113.7").andExpect(status().isCreated());
    submit("203.0.113.7").andExpect(status().isCreated());
    submit("203.0.113.7")
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    submit("203.0.113.8").andExpect(status().isCreated());
  }
}
