package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FiltersTest {

  /** A clock whose time the test moves. */
  static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-09-30T10:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
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
      return now;
    }
  }

  private static MockHttpServletResponse run(
      jakarta.servlet.Filter filter, MockHttpServletRequest request)
      throws ServletException, IOException {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    if (chain.getRequest() != null) {
      response.setStatus(299);
    }
    return response;
  }

  private static MockHttpServletRequest post(String uri, String remote, byte[] body) {
    MockHttpServletRequest r = new MockHttpServletRequest("POST", uri);
    r.setRemoteAddr(remote);
    if (body != null) {
      r.setContent(body);
    }
    return r;
  }

  @Test
  void requestSizeFilter() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(10);

    assertThat(run(filter, post("/api/registrations", "1.1.1.1", new byte[10])).getStatus())
        .isEqualTo(299);
    MockHttpServletResponse tooLarge =
        run(filter, post("/api/registrations", "1.1.1.1", new byte[11]));
    assertThat(tooLarge.getStatus()).isEqualTo(413);
    assertThat(tooLarge.getContentAsString()).contains("\"errors\":[]");
    assertThat(run(filter, post("/api/registrations", "1.1.1.1", null)).getStatus()).isEqualTo(411);
    MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/options");
    assertThat(run(filter, get).getStatus()).isEqualTo(299);
  }

  @Test
  void rateLimitPerClientAndWindow() throws Exception {
    MutableClock clock = new MutableClock();
    RateLimitFilter filter =
        new RateLimitFilter(
            List.of(
                new RateLimitFilter.Rule(
                    "registration",
                    HttpMethod.POST,
                    "/api/registrations",
                    2,
                    Duration.ofMinutes(10)),
                new RateLimitFilter.Rule("export", null, "/api/admin/", 1, Duration.ofMinutes(1))),
            clock);

    assertThat(run(filter, post("/api/registrations", "a", new byte[1])).getStatus())
        .isEqualTo(299);
    assertThat(run(filter, post("/api/registrations", "a", new byte[1])).getStatus())
        .isEqualTo(299);
    MockHttpServletResponse limited = run(filter, post("/api/registrations", "a", new byte[1]));
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isEqualTo("600");
    assertThat(run(filter, post("/api/registrations", "b", new byte[1])).getStatus())
        .isEqualTo(299);
    MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/registrations");
    get.setRemoteAddr("a");
    assertThat(run(filter, get).getStatus()).as("other method").isEqualTo(299);

    clock.advance(Duration.ofMinutes(10));
    assertThat(run(filter, post("/api/registrations", "a", new byte[1])).getStatus())
        .isEqualTo(299);

    MockHttpServletRequest export =
        new MockHttpServletRequest("GET", "/api/admin/registrations/export");
    export.setRemoteAddr("a");
    assertThat(run(filter, export).getStatus()).isEqualTo(299);
    assertThat(run(filter, export).getStatus()).isEqualTo(429);
  }

  @Test
  void rateLimitPurgesExpiredWindows() {
    MutableClock clock = new MutableClock();
    RateLimitFilter.Rule rule =
        new RateLimitFilter.Rule("r", HttpMethod.POST, "/x", 1, Duration.ofSeconds(1));
    RateLimitFilter filter = new RateLimitFilter(List.of(rule), clock);
    for (int i = 0; i < RateLimitFilter.MAX_TRACKED; i++) {
      assertThat(filter.allow(rule, "c" + i)).isTrue();
    }
    clock.advance(Duration.ofSeconds(2));

    assertThat(filter.allow(rule, "c0")).isTrue();
    assertThat(filter.allow(rule, "c0")).isFalse();
  }

  @Test
  void purgeKeepsWindowsThatAreStillActive() {
    MutableClock clock = new MutableClock();
    RateLimitFilter.Rule rule =
        new RateLimitFilter.Rule("r", HttpMethod.POST, "/x", 1, Duration.ofSeconds(10));
    RateLimitFilter filter = new RateLimitFilter(List.of(rule), clock);
    assertThat(filter.allow(rule, "attacker")).isTrue();
    for (int i = 1; i < RateLimitFilter.MAX_TRACKED; i++) {
      filter.allow(rule, "c" + i);
    }
    clock.advance(Duration.ofSeconds(9));

    assertThat(filter.allow(rule, "attacker")).as("window still active after purge").isFalse();

    clock.advance(Duration.ofSeconds(1));
    assertThat(filter.allow(rule, "attacker")).as("window expires exactly at its length").isTrue();
  }

  @Test
  void errorResponsesAreJsonWithoutSniffingOrCaching() throws Exception {
    MockHttpServletResponse r =
        run(new RequestSizeFilter(1), post("/api/registrations", "1.1.1.1", new byte[2]));

    assertThat(r.getContentType()).startsWith("application/json");
    assertThat(r.getCharacterEncoding()).isEqualToIgnoringCase("UTF-8");
    assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(r.getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(run(new RequestSizeFilter(1), post("/x", "1.1.1.1", new byte[0])).getStatus())
        .as("empty body with declared length 0")
        .isEqualTo(299);
  }

  @Test
  void organizerTransportFilter() throws Exception {
    OrganizerTransportFilter filter = new OrganizerTransportFilter(true);
    MockHttpServletRequest remotePlain = new MockHttpServletRequest("GET", "/api/admin/x");
    remotePlain.setRemoteAddr("203.0.113.7");
    MockHttpServletRequest remoteSecure = new MockHttpServletRequest("GET", "/api/admin/x");
    remoteSecure.setRemoteAddr("203.0.113.7");
    remoteSecure.setSecure(true);
    MockHttpServletRequest local = new MockHttpServletRequest("GET", "/api/admin/x");
    local.setRemoteAddr("127.0.0.1");
    MockHttpServletRequest publicPath = new MockHttpServletRequest("GET", "/api/options");
    publicPath.setRemoteAddr("203.0.113.7");

    assertThat(run(filter, remotePlain).getStatus()).isEqualTo(403);
    assertThat(run(filter, remoteSecure).getStatus()).isEqualTo(299);
    assertThat(run(filter, local).getStatus()).isEqualTo(299);
    assertThat(run(filter, publicPath).getStatus()).isEqualTo(299);
    assertThat(run(new OrganizerTransportFilter(false), remotePlain).getStatus()).isEqualTo(299);
  }

  @Test
  void loopbackDetectionUsesLiteralAddressesOnly() {
    assertThat(OrganizerTransportFilter.isLoopback("127.0.0.1")).isTrue();
    assertThat(OrganizerTransportFilter.isLoopback("127.5.6.7")).isTrue();
    assertThat(OrganizerTransportFilter.isLoopback("::1")).isTrue();
    assertThat(OrganizerTransportFilter.isLoopback("0:0:0:0:0:0:0:1")).isTrue();
    assertThat(OrganizerTransportFilter.isLoopback("10.0.0.1")).isFalse();
    assertThat(OrganizerTransportFilter.isLoopback("localhost")).isFalse();
    assertThat(OrganizerTransportFilter.isLoopback("")).isFalse();
    assertThat(OrganizerTransportFilter.isLoopback(null)).isFalse();
  }
}
