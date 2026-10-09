package si.konferenca.registration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Rate limits, body size limit and organizer HTTPS rule (SR-03, SR-06). */
class FiltersTest {

  private static MockHttpServletRequest request(String method, String uri, String address) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRemoteAddr(address);
    return request;
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

  private static int status(
      jakarta.servlet.Filter filter, MockHttpServletRequest request, MockHttpServletResponse out)
      throws Exception {
    filter.doFilter(request, out, new MockFilterChain());
    return out.getStatus();
  }

  @Test
  void rateLimitAllowsTheLimitPerMinuteAndClientThenRefuses() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-10-09T08:00:15Z"));
    RateLimitFilter filter =
        new RateLimitFilter(Map.of(RateLimitFilter.Group.REGISTRATIONS, 2), clock);

    assertThat(
            status(
                filter,
                request("POST", "/api/registrations", "1.1.1.1"),
                new MockHttpServletResponse()))
        .isEqualTo(200);
    assertThat(
            status(
                filter,
                request("POST", "/api/registrations", "1.1.1.1"),
                new MockHttpServletResponse()))
        .isEqualTo(200);
    MockHttpServletResponse refused = new MockHttpServletResponse();
    assertThat(status(filter, request("POST", "/api/registrations", "1.1.1.1"), refused))
        .isEqualTo(429);
    assertThat(refused.getHeader("Retry-After")).isEqualTo("45");
    assertThat(refused.getContentAsString()).contains("\"error\":\"rate_limited\"");

    assertThat(
            status(
                filter,
                request("POST", "/api/registrations", "2.2.2.2"),
                new MockHttpServletResponse()))
        .as("another client")
        .isEqualTo(200);
    assertThat(
            status(
                filter,
                request("GET", "/api/registration-form", "1.1.1.1"),
                new MockHttpServletResponse()))
        .as("group without a configured limit")
        .isEqualTo(200);
    assertThat(
            status(
                filter,
                request("GET", "/actuator/health", "1.1.1.1"),
                new MockHttpServletResponse()))
        .as("path outside every group")
        .isEqualTo(200);

    clock.now = Instant.parse("2026-10-09T08:01:00Z");
    assertThat(
            status(
                filter,
                request("POST", "/api/registrations", "1.1.1.1"),
                new MockHttpServletResponse()))
        .as("next minute")
        .isEqualTo(200);
  }

  @Test
  void eachGroupHasItsOwnLimit() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-10-09T08:00:59.500Z"));
    RateLimitFilter filter =
        new RateLimitFilter(
            Map.of(RateLimitFilter.Group.EXPORTS, 1, RateLimitFilter.Group.FORM, 1), clock);

    assertThat(
            status(filter, request("GET", "/api/export", "1.1.1.1"), new MockHttpServletResponse()))
        .isEqualTo(200);
    MockHttpServletResponse refused = new MockHttpServletResponse();
    assertThat(status(filter, request("GET", "/api/export", "1.1.1.1"), refused)).isEqualTo(429);
    assertThat(refused.getHeader("Retry-After")).isEqualTo("1");
    assertThat(
            status(
                filter,
                request("GET", "/api/registration-form", "1.1.1.1"),
                new MockHttpServletResponse()))
        .isEqualTo(200);
    assertThat(
            status(
                filter, request("POST", "/api/export", "1.1.1.1"), new MockHttpServletResponse()))
        .as("other method")
        .isEqualTo(200);
  }

  @Test
  void declaredOversizedBodyIsRefusedAtOnce() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(10);
    MockHttpServletRequest request = request("POST", "/api/registrations", "1.1.1.1");
    request.setContent("01234567890".getBytes(StandardCharsets.UTF_8));
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThat(status(filter, request, response)).isEqualTo(413);
    assertThat(response.getContentAsString()).contains("payload_too_large");
  }

  @Test
  void undeclaredOversizedBodyFailsWhileRead() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(10);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/registrations") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    request.setContent("0123456789AB".getBytes(StandardCharsets.UTF_8));
    AtomicReference<Throwable> failure = new AtomicReference<>();

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> {
          byte[] buffer = new byte[4];
          try {
            var in = req.getInputStream();
            assertThat(req.getInputStream()).isSameAs(in);
            while (in.read(buffer, 0, 4) > 0) {
              // read everything
            }
          } catch (RequestSizeFilter.PayloadTooLargeException e) {
            failure.set(e);
          }
        });

    assertThat(failure.get()).isNotNull();
  }

  @Test
  void bodyWithinTheLimitAndPathsOutsideApiPass() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(10);
    MockHttpServletRequest small = request("POST", "/api/registrations", "1.1.1.1");
    small.setContent("0123456789".getBytes(StandardCharsets.UTF_8));
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(small, new MockHttpServletResponse(), chain);
    assertThat(chain.getRequest()).isNotNull();
    assertThat(chain.getRequest().getInputStream().readAllBytes()).hasSize(10);
    assertThat(chain.getRequest().getInputStream().read()).isEqualTo(-1);

    MockHttpServletRequest other = request("POST", "/actuator/x", "1.1.1.1");
    other.setContent(new byte[100]);
    assertThat(status(filter, other, new MockHttpServletResponse())).isEqualTo(200);
  }

  @Test
  void singleByteReadsAreCountedToo() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(2);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/registrations") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    request.setContent(new byte[] {1, 2, 3});

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> {
          var in = req.getInputStream();
          assertThat(in.read()).isEqualTo(1);
          assertThat(in.read()).isEqualTo(2);
          assertThatThrownBy(in::read)
              .isInstanceOf(RequestSizeFilter.PayloadTooLargeException.class);
        });
  }

  @Test
  void exportOverPlainHttpFromOutsideIsRefused() throws Exception {
    OrganizerHttpsFilter filter = new OrganizerHttpsFilter(true);
    MockHttpServletResponse response = new MockHttpServletResponse();

    assertThat(status(filter, request("GET", "/api/export", "203.0.113.5"), response))
        .isEqualTo(403);
    assertThat(response.getContentAsString()).contains("https_required");
  }

  @Test
  void exportOverHttpsOrFromLoopbackOrWithTheRuleOffPasses() throws Exception {
    MockHttpServletRequest secure = request("GET", "/api/export", "203.0.113.5");
    secure.setSecure(true);
    assertThat(status(new OrganizerHttpsFilter(true), secure, new MockHttpServletResponse()))
        .isEqualTo(200);
    assertThat(
            status(
                new OrganizerHttpsFilter(true),
                request("GET", "/api/export", "127.0.0.1"),
                new MockHttpServletResponse()))
        .isEqualTo(200);
    assertThat(
            status(
                new OrganizerHttpsFilter(true),
                request("GET", "/api/export", "::1"),
                new MockHttpServletResponse()))
        .isEqualTo(200);
    assertThat(
            status(
                new OrganizerHttpsFilter(false),
                request("GET", "/api/export", "203.0.113.5"),
                new MockHttpServletResponse()))
        .isEqualTo(200);
    assertThat(
            status(
                new OrganizerHttpsFilter(true),
                request("POST", "/api/registrations", "203.0.113.5"),
                new MockHttpServletResponse()))
        .as("other paths")
        .isEqualTo(200);
  }

  @Test
  void onlyLiteralLoopbackAddressesCount() {
    assertThat(OrganizerHttpsFilter.isLoopback("127.0.0.2")).isTrue();
    assertThat(OrganizerHttpsFilter.isLoopback("0:0:0:0:0:0:0:1")).isTrue();
    assertThat(OrganizerHttpsFilter.isLoopback("10.0.0.1")).isFalse();
    assertThat(OrganizerHttpsFilter.isLoopback("localhost")).isFalse();
    assertThat(OrganizerHttpsFilter.isLoopback("")).isFalse();
    assertThat(OrganizerHttpsFilter.isLoopback(null)).isFalse();
  }
}
