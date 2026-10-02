package si.konferenca.registration.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** The rate limit and the client identity it counts for (SR-03, SB-06). */
class RateLimitFilterTest {

  /** A clock the test moves by hand. */
  private static final class TestClock extends Clock {
    private final AtomicLong millis =
        new AtomicLong(Instant.parse("2026-01-01T10:00:05Z").toEpochMilli());

    void advanceSeconds(long seconds) {
      millis.addAndGet(seconds * 1000);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(millis.get());
    }
  }

  private final TestClock clock = new TestClock();
  private final RateLimitFilter filter =
      new RateLimitFilter(new RateLimitFilter.Limits(2, 1, 3), new ClientRequests(false), clock);

  private MockHttpServletResponse send(String method, String path, String remoteAddress)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setRemoteAddr(remoteAddress);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  @Test
  void sr03_registrationsOverTheLimitAreRefusedWithRetryAfter() throws Exception {
    assertThat(send("POST", "/api/registrations", "10.0.0.1").getStatus()).isEqualTo(200);
    assertThat(send("POST", "/api/registrations", "10.0.0.1").getStatus()).isEqualTo(200);

    MockHttpServletResponse refused = send("POST", "/api/registrations", "10.0.0.1");

    assertThat(refused.getStatus()).isEqualTo(429);
    assertThat(refused.getHeader("Retry-After")).isEqualTo("55");
    assertThat(refused.getContentType()).startsWith("application/problem+json");
    assertThat(refused.getContentAsString()).contains("\"status\":429");
  }

  @Test
  void sr03_limitsAreCountedPerClientAndPerGroup() throws Exception {
    send("POST", "/api/registrations", "10.0.0.1");
    send("POST", "/api/registrations", "10.0.0.1");
    assertThat(send("POST", "/api/registrations", "10.0.0.1").getStatus()).isEqualTo(429);

    assertThat(send("POST", "/api/registrations", "10.0.0.2").getStatus()).isEqualTo(200);
    assertThat(send("GET", "/api/options", "10.0.0.1").getStatus()).isEqualTo(200);
    assertThat(send("GET", "/api/registrations/export", "10.0.0.1").getStatus()).isEqualTo(200);
    assertThat(send("GET", "/api/registrations/export", "10.0.0.1").getStatus()).isEqualTo(429);
  }

  @Test
  void sr03_aNewWindowAllowsRequestsAgain() throws Exception {
    send("GET", "/api/registrations/export", "10.0.0.1");
    assertThat(send("GET", "/api/registrations/export", "10.0.0.1").getStatus()).isEqualTo(429);

    clock.advanceSeconds(55);

    assertThat(send("GET", "/api/registrations/export", "10.0.0.1").getStatus()).isEqualTo(200);
  }

  @Test
  void requestsOutsideTheApiAreNotCounted() throws Exception {
    for (int i = 0; i < 10; i++) {
      assertThat(send("GET", "/actuator/health", "10.0.0.1").getStatus()).isEqualTo(200);
    }
  }

  @Test
  void forwardedHeadersAreIgnoredUnlessTheProxyIsTrusted() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/options");
    request.setRemoteAddr("172.18.0.5");
    request.addHeader("X-Forwarded-For", "203.0.113.9, 198.51.100.7");
    request.addHeader("X-Forwarded-Proto", "https");

    assertThat(new ClientRequests(false).clientAddress(request)).isEqualTo("172.18.0.5");
    assertThat(new ClientRequests(false).isHttps(request)).isFalse();
    // The last entry is the one the trusted proxy appended; the first could be forged.
    assertThat(new ClientRequests(true).clientAddress(request)).isEqualTo("198.51.100.7");
    assertThat(new ClientRequests(true).isHttps(request)).isTrue();
  }

  @Test
  void trustedProxyWithoutForwardedHeaderFallsBackToTheConnection() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/options");
    request.setRemoteAddr("172.18.0.5");
    request.setSecure(true);

    assertThat(new ClientRequests(true).clientAddress(request)).isEqualTo("172.18.0.5");
    assertThat(new ClientRequests(false).isHttps(request)).isTrue();
  }
}
