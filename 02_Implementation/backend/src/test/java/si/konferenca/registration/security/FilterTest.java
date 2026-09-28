package si.konferenca.registration.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import si.konferenca.registration.config.AppProperties;

class FilterTest {

  private static AppProperties properties(int registrationLimit, long maxBody) {
    return new AppProperties(
        new AppProperties.Recaptcha(true, null, null, "http://localhost"),
        new AppProperties.Mail("from@test", List.of()),
        new AppProperties.Backup("./build"),
        new AppProperties.Options(null),
        new AppProperties.Organizer("organizer", null),
        new AppProperties.RateLimit(
            new AppProperties.Limit(registrationLimit, 60), new AppProperties.Limit(1, 60)),
        new AppProperties.Request(maxBody));
  }

  /** Clock whose time can be advanced. */
  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  private static MockHttpServletResponse run(
      jakarta.servlet.Filter filter, MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  private static MockHttpServletRequest registration(String ip) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/registrations/external");
    request.setRemoteAddr(ip);
    return request;
  }

  @Test
  void rateLimitAllowsUpToLimitThenRejectsUntilWindowResets() throws Exception {
    MutableClock clock = new MutableClock();
    RateLimitFilter filter = new RateLimitFilter(properties(2, 1024), clock);

    assertThat(run(filter, registration("10.0.0.1")).getStatus()).isEqualTo(200);
    assertThat(run(filter, registration("10.0.0.1")).getStatus()).isEqualTo(200);
    MockHttpServletResponse limited = run(filter, registration("10.0.0.1"));
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
    assertThat(limited.getContentAsString()).contains("\"error\":\"RATE_LIMITED\"");
    assertThat(run(filter, registration("10.0.0.2")).getStatus()).isEqualTo(200);

    clock.now = clock.now.plusSeconds(61);
    assertThat(run(filter, registration("10.0.0.1")).getStatus()).isEqualTo(200);
  }

  @Test
  void rateLimitIgnoresOtherEndpoints() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(properties(1, 1024), new MutableClock());
    for (int i = 0; i < 5; i++) {
      assertThat(run(filter, new MockHttpServletRequest("GET", "/api/form-config")).getStatus())
          .isEqualTo(200);
    }
  }

  @Test
  void rateLimitAppliesToOrganizerEndpoint() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(properties(10, 1024), new MutableClock());
    MockHttpServletRequest first =
        new MockHttpServletRequest("GET", "/api/organizer/registrations.xlsx");
    MockHttpServletRequest second =
        new MockHttpServletRequest("GET", "/api/organizer/registrations.xlsx");

    assertThat(run(filter, first).getStatus()).isEqualTo(200);
    assertThat(run(filter, second).getStatus()).isEqualTo(429);
  }

  @Test
  void requestSizeFilterRejectsDeclaredOversizedBody() throws Exception {
    RequestSizeLimitFilter filter = new RequestSizeLimitFilter(properties(10, 100));
    MockHttpServletRequest request = registration("10.0.0.1");
    request.setContent(new byte[101]);

    MockHttpServletResponse response = run(filter, request);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
  }

  @Test
  void requestSizeFilterFailsReadingUndeclaredOversizedBody() throws Exception {
    RequestSizeLimitFilter filter = new RequestSizeLimitFilter(properties(10, 100));
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/registrations/external") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    request.setContent(new byte[500]);
    AtomicReference<HttpServletRequest> seen = new AtomicReference<>();

    filter.doFilter(
        request, new MockHttpServletResponse(), (req, res) -> seen.set((HttpServletRequest) req));

    assertThatThrownBy(() -> seen.get().getInputStream().readAllBytes())
        .isInstanceOf(IOException.class);
  }

  @Test
  void requestSizeFilterPassesSmallBodyAndIgnoresNonApiPaths() throws Exception {
    RequestSizeLimitFilter filter = new RequestSizeLimitFilter(properties(10, 100));
    MockHttpServletRequest small = registration("10.0.0.1");
    small.setContent(new byte[50]);
    assertThat(run(filter, small).getStatus()).isEqualTo(200);

    MockHttpServletRequest nonApi = new MockHttpServletRequest("POST", "/other");
    nonApi.setContent(new byte[500]);
    assertThat(run(filter, nonApi).getStatus()).isEqualTo(200);
  }
}
