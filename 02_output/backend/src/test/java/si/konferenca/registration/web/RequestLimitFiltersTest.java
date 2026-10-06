package si.konferenca.registration.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLimitFiltersTest {

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-10-06T08:00:00Z");

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

  private static MockHttpServletRequest request(String method, String path, String address) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setRemoteAddr(address);
    return request;
  }

  private static int call(
      RateLimitFilter filter, MockHttpServletRequest request, AtomicInteger passed)
      throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, (req, res) -> passed.incrementAndGet());
    return response.getStatus();
  }

  @Test
  void registrationsOverTheLimitGet429WithRetryAfterUntilTheWindowEnds() throws Exception {
    MutableClock clock = new MutableClock();
    RateLimitFilter filter = new RateLimitFilter(2, 5, 5, clock);
    AtomicInteger passed = new AtomicInteger();

    assertThat(call(filter, request("POST", "/api/registrations", "10.0.0.1"), passed))
        .isEqualTo(200);
    assertThat(call(filter, request("POST", "/api/registrations", "10.0.0.1"), passed))
        .isEqualTo(200);
    MockHttpServletResponse limited = new MockHttpServletResponse();
    clock.now = clock.now.plusSeconds(20);
    filter.doFilter(
        request("POST", "/api/registrations", "10.0.0.1"),
        limited,
        (req, res) -> passed.incrementAndGet());

    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isEqualTo("40");
    assertThat(limited.getContentType()).isEqualTo("application/problem+json");
    assertThat(limited.getContentAsString()).contains("\"status\":429");
    assertThat(passed).hasValue(2);

    assertThat(call(filter, request("POST", "/api/registrations", "10.0.0.2"), passed))
        .isEqualTo(200);
    clock.now = clock.now.plusSeconds(40);
    assertThat(call(filter, request("POST", "/api/registrations", "10.0.0.1"), passed))
        .isEqualTo(200);
  }

  @Test
  void groupsAreCountedSeparatelyAndOtherRequestsAreNotLimited() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(1, 1, 1, new MutableClock());
    AtomicInteger passed = new AtomicInteger();

    assertThat(call(filter, request("POST", "/api/registrations", "a"), passed)).isEqualTo(200);
    assertThat(call(filter, request("GET", "/api/registrations/export", "a"), passed))
        .isEqualTo(200);
    assertThat(call(filter, request("GET", "/api/form-config", "a"), passed)).isEqualTo(200);
    assertThat(call(filter, request("GET", "/api/registrations/export", "a"), passed))
        .isEqualTo(429);
    assertThat(call(filter, request("GET", "/api/form-config", "a"), passed)).isEqualTo(429);
    assertThat(call(filter, request("GET", "/api/registrations", "a"), passed)).isEqualTo(200);
    assertThat(call(filter, request("GET", "/actuator/health", "a"), passed)).isEqualTo(200);
    assertThat(call(filter, request("GET", "/actuator/health", "a"), passed)).isEqualTo(200);
  }

  @Test
  void bodyLargerThanTheDeclaredLimitIsRefusedBeforeReading() throws Exception {
    RequestSizeLimitFilter filter = new RequestSizeLimitFilter(10);
    MockHttpServletRequest request = request("POST", "/api/registrations", "a");
    request.setContent(new byte[11]);
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicInteger passed = new AtomicInteger();

    filter.doFilter(request, response, (req, res) -> passed.incrementAndGet());

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString()).contains("\"status\":413");
    assertThat(passed).hasValue(0);
  }

  @Test
  void bodyAtTheLimitIsReadCompletely() throws Exception {
    RequestSizeLimitFilter filter = new RequestSizeLimitFilter(10);
    MockHttpServletRequest request = request("POST", "/api/registrations", "a");
    request.setContent(new byte[10]);
    AtomicReference<byte[]> read = new AtomicReference<>();

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> read.set(req.getInputStream().readAllBytes()));

    assertThat(read.get()).hasSize(10);
  }

  @Test
  void bodyWithoutLengthIsCutOffWhileReading() throws Exception {
    RequestSizeLimitFilter filter = new RequestSizeLimitFilter(10);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/registrations") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    request.setContent(new byte[25]);
    AtomicReference<Exception> failure = new AtomicReference<>();
    FilterChain chain =
        (req, res) -> {
          try {
            var in = req.getInputStream();
            in.read();
            in.read(new byte[100], 0, 100);
          } catch (IOException e) {
            failure.set(e);
          }
        };

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(failure.get()).isNotNull();
    assertThat(
            RequestSizeLimitFilter.isTooLarge(
                new HttpMessageNotReadableException("x", failure.get(), null)))
        .isTrue();
    assertThat(
            RequestSizeLimitFilter.isTooLarge(new IllegalStateException(new IOException("other"))))
        .isFalse();
  }

  @Test
  void problemJsonTextHasTheContractFields() {
    assertThat(Problems.json(org.springframework.http.HttpStatus.FORBIDDEN, "Forbidden", "No."))
        .isEqualTo(
            "{\"type\":\"about:blank\",\"title\":\"Forbidden\",\"status\":403,\"detail\":\"No.\"}");
    assertThat(
            Problems.of(org.springframework.http.HttpStatus.BAD_REQUEST, "t", "d")
                .getBody()
                .errors())
        .isNull();
  }
}
