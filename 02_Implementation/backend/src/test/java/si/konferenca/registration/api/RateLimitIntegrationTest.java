package si.konferenca.registration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import si.konferenca.registration.support.PostgresIntegrationTest;
import si.konferenca.registration.support.TestData;

/** Spec §7.5: registration and organizer requests are rate limited per client IP. */
@SpringBootTest
@AutoConfigureMockMvc
class RateLimitIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private JavaMailSender mailSender;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    PostgresIntegrationTest.baseProperties(registry);
    registry.add("app.rate-limit.registration.requests", () -> "2");
    registry.add("app.rate-limit.organizer.requests", () -> "2");
  }

  private MvcResult register(String ip) throws Exception {
    Map<String, Object> body = TestData.external();
    body.remove("consents"); // invalid on purpose: nothing is stored, but the request counts
    return mockMvc
        .perform(
            post("/api/registrations/external")
                .with(
                    request -> {
                      request.setRemoteAddr(ip);
                      return request;
                    })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)))
        .andReturn();
  }

  @Test
  void registrationRequestsBeyondLimitAreRejected() throws Exception {
    assertThat(register("198.51.100.1").getResponse().getStatus()).isEqualTo(400);
    assertThat(register("198.51.100.1").getResponse().getStatus()).isEqualTo(400);

    MvcResult limited = register("198.51.100.1");

    assertThat(limited.getResponse().getStatus()).isEqualTo(429);
    assertThat(limited.getResponse().getHeader("Retry-After")).isNotBlank();
    assertThat(limited.getResponse().getContentAsString()).contains("RATE_LIMITED");
    assertThat(register("198.51.100.2").getResponse().getStatus()).isEqualTo(400);
  }

  @Test
  void organizerRequestsBeyondLimitAreRejected() throws Exception {
    for (int i = 0; i < 2; i++) {
      MvcResult result =
          mockMvc
              .perform(
                  get("/api/organizer/registrations.xlsx")
                      .with(
                          request -> {
                            request.setRemoteAddr("203.0.113.9");
                            return request;
                          }))
              .andReturn();
      assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
    MvcResult limited =
        mockMvc
            .perform(
                get("/api/organizer/registrations.xlsx")
                    .with(
                        request -> {
                          request.setRemoteAddr("203.0.113.9");
                          return request;
                        }))
            .andReturn();
    assertThat(limited.getResponse().getStatus()).isEqualTo(429);
  }
}
