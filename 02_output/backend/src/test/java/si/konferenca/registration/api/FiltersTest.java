package si.konferenca.registration.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.config.StartupChecksTest;

class FiltersTest {

  private static AppProperties props(
      int registrations, int exports, long maxBytes, boolean httpsOnly) {
    AppProperties p = StartupChecksTest.validProduction();
    return new AppProperties(
        p.environment(),
        p.conferenceName(),
        p.optionsFile(),
        p.jsonCopyDir(),
        p.mailFrom(),
        p.recaptcha(),
        new AppProperties.Organizer("org", "x".repeat(16), "a@b.si", httpsOnly),
        p.corsAllowedOrigin(),
        new AppProperties.RateLimit(registrations, exports),
        maxBytes,
        p.retention());
  }

  private static MockHttpServletRequest request(String method, String uri, String remote) {
    MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
    r.setRequestURI(uri);
    r.setRemoteAddr(remote);
    return r;
  }

  private static final class Counting implements FilterChain {
    int calls;
    final AtomicReference<String> body = new AtomicReference<>();

    @Override
    public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res)
        throws java.io.IOException {
      calls++;
      body.set(
          new String(
              ((HttpServletRequest) req).getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }
  }

  @Test
  @DisplayName("SR-03 the registration limit applies per client address and minute")
  void rateLimitPerAddressAndWindow() throws Exception {
    Clock clock = Clock.fixed(Instant.parse("2026-10-08T10:00:10Z"), ZoneOffset.UTC);
    RateLimitFilter filter = new RateLimitFilter(props(2, 1, 100, true), clock);
    Counting chain = new Counting();
    for (int i = 0; i < 2; i++) {
      filter.doFilter(
          request("POST", "/api/registrations", "10.0.0.1"), new MockHttpServletResponse(), chain);
    }
    MockHttpServletResponse limited = new MockHttpServletResponse();
    filter.doFilter(request("POST", "/api/registrations", "10.0.0.1"), limited, chain);
    filter.doFilter(
        request("POST", "/api/registrations", "10.0.0.2"), new MockHttpServletResponse(), chain);

    assertThat(chain.calls).isEqualTo(3);
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isEqualTo("50");
    assertThat(limited.getContentType()).startsWith("application/problem+json");
  }

  @Test
  void windowResetsAfterAMinute() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-10-08T10:00:59Z"));
    RateLimitFilter filter = new RateLimitFilter(props(1, 1, 100, true), clock);
    Counting chain = new Counting();
    filter.doFilter(request("GET", "/api/export", "1.1.1.1"), new MockHttpServletResponse(), chain);
    MockHttpServletResponse limited = new MockHttpServletResponse();
    filter.doFilter(request("GET", "/api/export", "1.1.1.1"), limited, chain);
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isEqualTo("1");

    clock.now = Instant.parse("2026-10-08T10:01:00Z");
    MockHttpServletResponse later = new MockHttpServletResponse();
    filter.doFilter(request("GET", "/api/export", "1.1.1.1"), later, chain);
    assertThat(later.getStatus()).isEqualTo(200);
    assertThat(chain.calls).isEqualTo(2);
  }

  @Test
  @DisplayName("SB-06 tracked addresses from past windows are evicted, so memory stays bounded")
  void staleWindowsAreEvicted() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-10-08T10:00:00Z"));
    RateLimitFilter filter = new RateLimitFilter(props(1, 1, 100, true), clock);
    Counting chain = new Counting();
    for (int i = 0; i <= 10_001; i++) {
      filter.doFilter(
          request("POST", "/api/registrations", "10.0." + (i / 250) + "." + (i % 250)),
          new MockHttpServletResponse(),
          chain);
    }
    clock.now = Instant.parse("2026-10-08T10:01:00Z");
    filter.doFilter(
        request("POST", "/api/registrations", "10.9.9.9"), new MockHttpServletResponse(), chain);
    java.lang.reflect.Field field = RateLimitFilter.class.getDeclaredField("windows");
    field.setAccessible(true);
    assertThat(((java.util.Map<?, ?>) field.get(filter)).size()).isEqualTo(1);
  }

  @Test
  void otherRequestsAreNotLimited() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(props(1, 1, 100, true), Clock.systemUTC());
    Counting chain = new Counting();
    for (int i = 0; i < 5; i++) {
      filter.doFilter(request("GET", "/api/form", "1.1.1.1"), new MockHttpServletResponse(), chain);
      filter.doFilter(
          request("GET", "/api/registrations", "1.1.1.1"), new MockHttpServletResponse(), chain);
    }
    assertThat(chain.calls).isEqualTo(10);
  }

  @Test
  @DisplayName("SR-03 a declared length over the limit is refused with 413")
  void declaredLengthOverLimit() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(props(1, 1, 10, true));
    MockHttpServletRequest r = request("POST", "/api/registrations", "1.1.1.1");
    r.setContent("01234567890".getBytes(StandardCharsets.UTF_8));
    MockHttpServletResponse response = new MockHttpServletResponse();
    Counting chain = new Counting();

    filter.doFilter(r, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(chain.calls).isZero();
  }

  @Test
  @DisplayName("SR-03 an undeclared (chunked) body over the limit is refused too")
  void undeclaredLengthOverLimit() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(props(1, 1, 10, true));
    MockHttpServletRequest r =
        new MockHttpServletRequest("POST", "/api/registrations") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    r.setContent("01234567890".getBytes(StandardCharsets.UTF_8));
    MockHttpServletResponse response = new MockHttpServletResponse();
    Counting chain = new Counting();

    filter.doFilter(r, response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(chain.calls).isZero();
  }

  @Test
  void bodyWithinLimitIsPassedOnUnchanged() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(props(1, 1, 10, true));
    MockHttpServletRequest r = request("POST", "/api/registrations", "1.1.1.1");
    r.setContent("0123456789".getBytes(StandardCharsets.UTF_8));
    Counting chain = new Counting();

    filter.doFilter(r, new MockHttpServletResponse(), chain);

    assertThat(chain.calls).isEqualTo(1);
    assertThat(chain.body.get()).isEqualTo("0123456789");
  }

  @Test
  @DisplayName(
      "SR-06 export over plain HTTP from a remote address is refused before authentication")
  void httpsOnly() throws Exception {
    HttpsOnlyFilter filter = new HttpsOnlyFilter(props(1, 1, 10, true));
    Counting chain = new Counting();

    MockHttpServletResponse remotePlain = new MockHttpServletResponse();
    filter.doFilter(request("GET", "/api/export", "203.0.113.5"), remotePlain, chain);
    assertThat(remotePlain.getStatus()).isEqualTo(403);

    MockHttpServletRequest secure = request("GET", "/api/export", "203.0.113.5");
    secure.setSecure(true);
    filter.doFilter(secure, new MockHttpServletResponse(), chain);
    filter.doFilter(
        request("GET", "/api/export", "127.0.0.1"), new MockHttpServletResponse(), chain);
    filter.doFilter(request("GET", "/api/export", "::1"), new MockHttpServletResponse(), chain);
    filter.doFilter(
        request("GET", "/api/form", "203.0.113.5"), new MockHttpServletResponse(), chain);
    assertThat(chain.calls).isEqualTo(4);
  }

  @Test
  void httpsOnlyCanBeSwitchedOffLocally() throws Exception {
    HttpsOnlyFilter filter = new HttpsOnlyFilter(props(1, 1, 10, false));
    Counting chain = new Counting();
    filter.doFilter(
        request("GET", "/api/export", "172.18.0.5"), new MockHttpServletResponse(), chain);
    assertThat(chain.calls).isEqualTo(1);
  }

  private static final class MutableClock extends Clock {
    Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    @Override
    public java.time.ZoneId getZone() {
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
}
