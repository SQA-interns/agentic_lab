package si.konferenca.registration.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FiltersTest {

  private final AtomicInteger passed = new AtomicInteger();
  private final FilterChain chain = (req, res) -> passed.incrementAndGet();

  private static MockHttpServletRequest request(String method, String uri, String address) {
    MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
    r.setRemoteAddr(address);
    return r;
  }

  @Test
  void rateLimitAllowsUpToTheLimitPerAddressAndGroup() throws Exception {
    RateLimitFilter f =
        new RateLimitFilter(
            2, 1, 5, Clock.fixed(Instant.parse("2026-10-06T18:00:30Z"), ZoneOffset.UTC));
    MockHttpServletResponse last = null;
    for (int i = 0; i < 3; i++) {
      last = new MockHttpServletResponse();
      f.doFilter(request("POST", "/api/registrations", "10.0.0.1"), last, chain);
    }

    assertThat(passed.get()).isEqualTo(2);
    assertThat(last.getStatus()).isEqualTo(429);
    assertThat(last.getHeader("Retry-After")).isEqualTo("60");
    assertThat(last.getContentAsString()).contains("RATE_LIMITED");

    f.doFilter(
        request("POST", "/api/registrations", "10.0.0.2"), new MockHttpServletResponse(), chain);
    f.doFilter(request("GET", "/api/options", "10.0.0.1"), new MockHttpServletResponse(), chain);
    f.doFilter(
        request("GET", "/actuator/health", "10.0.0.1"), new MockHttpServletResponse(), chain);
    assertThat(passed.get()).isEqualTo(5);
  }

  @Test
  void rateLimitExportGroupAndWindowReset() {
    RateLimitFilter f = new RateLimitFilter(1, 1, 1, Clock.systemUTC());

    assertThat(f.acquire("k", 1, 0)).isZero();
    assertThat(f.acquire("k", 1, 59_000)).isEqualTo(1);
    assertThat(f.acquire("k", 1, 60_000)).isZero();
  }

  @Test
  void expiredWindowsAreEvictedWhenTheMapIsFull() {
    RateLimitFilter f = new RateLimitFilter(1, 1, 1, Clock.systemUTC());
    for (int i = 0; i <= RateLimitFilter.MAX_TRACKED; i++) {
      f.acquire("k" + i, 1, 0);
    }
    f.acquire("fresh", 1, 59_999);
    assertThat(f.trackedWindows()).isEqualTo(RateLimitFilter.MAX_TRACKED + 2);

    f.acquire("later", 1, 60_000);

    assertThat(f.trackedWindows()).isEqualTo(2);
  }

  @Test
  void optionsGroupHasItsOwnLimit() throws Exception {
    RateLimitFilter f = new RateLimitFilter(9, 9, 1, Clock.systemUTC());
    MockHttpServletResponse second = new MockHttpServletResponse();
    f.doFilter(request("GET", "/api/options", "1.2.3.4"), new MockHttpServletResponse(), chain);
    f.doFilter(request("GET", "/api/options", "1.2.3.4"), second, chain);

    assertThat(second.getStatus()).isEqualTo(429);
    assertThat(passed.get()).isEqualTo(1);
  }

  @Test
  void bodyOfExactlyTheLimitPassesAndBytesAreReturned() throws Exception {
    MockHttpServletRequest r = request("POST", "/api/registrations", "1.1.1.1");
    r.setContent(new byte[] {7, 8, 9});
    FilterChain reading =
        (req, res) -> {
          var in = req.getInputStream();
          assertThat(in.read()).isEqualTo(7);
          assertThat(in.read(new byte[4], 0, 4)).isEqualTo(2);
          assertThat(in.read()).isEqualTo(-1);
          passed.incrementAndGet();
        };

    new RequestSizeFilter(3).doFilter(r, new MockHttpServletResponse(), reading);

    assertThat(passed.get()).isEqualTo(1);
  }

  @Test
  void exportIsRateLimited() throws Exception {
    RateLimitFilter f = new RateLimitFilter(9, 1, 9, Clock.systemUTC());
    MockHttpServletResponse second = new MockHttpServletResponse();
    f.doFilter(
        request("GET", "/api/admin/registrations/export", "1.2.3.4"),
        new MockHttpServletResponse(),
        chain);
    f.doFilter(request("GET", "/api/admin/registrations/export", "1.2.3.4"), second, chain);

    assertThat(second.getStatus()).isEqualTo(429);
  }

  @Test
  void declaredLengthOverLimitIsRefused() throws Exception {
    MockHttpServletRequest r = request("POST", "/api/registrations", "1.1.1.1");
    r.setContent(new byte[20]);
    MockHttpServletResponse res = new MockHttpServletResponse();

    new RequestSizeFilter(10).doFilter(r, res, chain);

    assertThat(res.getStatus()).isEqualTo(413);
    assertThat(res.getContentType()).startsWith("application/problem+json");
    assertThat(passed.get()).isZero();
  }

  @Test
  void streamedBodyOverLimitFailsWhileReading() throws Exception {
    MockHttpServletRequest r =
        new MockHttpServletRequest("POST", "/api/registrations") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    r.setContent(new byte[20]);
    AtomicInteger read = new AtomicInteger();
    FilterChain reading =
        (req, res) -> {
          var in = req.getInputStream();
          assertThat(in.read(new byte[8], 0, 8)).isEqualTo(8);
          assertThat(in.read()).isZero();
          assertThat(in.read()).isZero();
          read.incrementAndGet();
          assertThatThrownBy(in::read)
              .isInstanceOf(IOException.class)
              .satisfies(e -> assertThat(RequestSizeFilter.isTooLarge(e)).isTrue());
        };

    new RequestSizeFilter(10).doFilter(r, new MockHttpServletResponse(), reading);

    assertThat(read.get()).isEqualTo(1);
    assertThat(RequestSizeFilter.isTooLarge(new RuntimeException(new IllegalStateException())))
        .isFalse();
  }

  @Test
  void bulkReadOverLimitFails() throws Exception {
    MockHttpServletRequest r =
        new MockHttpServletRequest("POST", "/api/registrations") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    r.setContent(new byte[20]);
    AtomicInteger checked = new AtomicInteger();
    FilterChain reading =
        (req, res) -> {
          var in = req.getInputStream();
          assertThatThrownBy(() -> in.read(new byte[16], 0, 16))
              .satisfies(e -> assertThat(RequestSizeFilter.isTooLarge(e)).isTrue());
          checked.incrementAndGet();
        };

    new RequestSizeFilter(10).doFilter(r, new MockHttpServletResponse(), reading);

    assertThat(checked.get()).isEqualTo(1);
  }

  @Test
  void httpsOnlyRefusesPlainHttpFromRemoteClients() throws Exception {
    HttpsOnlyFilter f = new HttpsOnlyFilter(true);
    MockHttpServletResponse remote = new MockHttpServletResponse();

    f.doFilter(request("GET", "/api/admin/registrations/export", "203.0.113.5"), remote, chain);

    assertThat(remote.getStatus()).isEqualTo(403);
    assertThat(remote.getContentAsString()).contains("HTTPS_REQUIRED");
    assertThat(passed.get()).isZero();
  }

  @Test
  void httpsOnlyAllowsSecureLoopbackOtherPathsAndDisabledMode() throws Exception {
    MockHttpServletRequest secure =
        request("GET", "/api/admin/registrations/export", "203.0.113.5");
    secure.setSecure(true);
    new HttpsOnlyFilter(true).doFilter(secure, new MockHttpServletResponse(), chain);
    new HttpsOnlyFilter(true)
        .doFilter(
            request("GET", "/api/admin/x", "127.0.0.1"), new MockHttpServletResponse(), chain);
    new HttpsOnlyFilter(true)
        .doFilter(
            request("GET", "/api/admin/x", "0:0:0:0:0:0:0:1"),
            new MockHttpServletResponse(),
            chain);
    new HttpsOnlyFilter(true)
        .doFilter(
            request("GET", "/api/options", "203.0.113.5"), new MockHttpServletResponse(), chain);
    new HttpsOnlyFilter(false)
        .doFilter(
            request("GET", "/api/admin/x", "203.0.113.5"), new MockHttpServletResponse(), chain);

    assertThat(passed.get()).isEqualTo(5);
    assertThat(HttpsOnlyFilter.isLoopback("::1")).isTrue();
    assertThat(HttpsOnlyFilter.isLoopback(null)).isFalse();
    assertThat(HttpsOnlyFilter.isLoopback("10.0.0.1")).isFalse();
  }

  @Test
  void unexpectedErrorsHaveNoDetails() {
    var r = new ApiExceptionHandler().unexpected(new IllegalStateException("secret internals"));

    assertThat(r.getStatusCode().value()).isEqualTo(500);
    assertThat(r.getBody().toString()).doesNotContain("secret internals");
  }
}
