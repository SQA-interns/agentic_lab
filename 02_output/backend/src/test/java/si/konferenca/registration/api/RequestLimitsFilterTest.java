package si.konferenca.registration.api;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RequestLimitsFilterTest {

  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-10-05T10:00:00Z"));
  private final Clock clock =
      new Clock() {
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
          return now.get();
        }
      };
  private final RequestLimitsFilter filter =
      new RequestLimitsFilter(
          Map.of(
              RequestLimitsFilter.Group.REGISTRATIONS, 2,
              RequestLimitsFilter.Group.TOKENS, 1,
              RequestLimitsFilter.Group.EXPORTS, 1,
              RequestLimitsFilter.Group.FORM_CONFIG, 1),
          10,
          JsonMapper.builder().build(),
          clock);

  private static MockHttpServletRequest request(String method, String uri, String client) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRemoteAddr(client);
    return request;
  }

  private record Result(MockHttpServletResponse response, HttpServletRequest passed) {}

  private Result run(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    return new Result(response, (HttpServletRequest) chain.getRequest());
  }

  @Test
  void limitsEachClientPerGroupWithinAMinute() throws Exception {
    assertThat(run(request("POST", "/api/registrations", "1.1.1.1")).passed()).isNotNull();
    assertThat(run(request("POST", "/api/registrations", "1.1.1.1")).passed()).isNotNull();
    Result third = run(request("POST", "/api/registrations", "1.1.1.1"));

    assertThat(third.passed()).isNull();
    assertThat(third.response().getStatus()).isEqualTo(429);
    assertThat(third.response().getHeader("Retry-After")).isEqualTo("60");
    assertThat(third.response().getContentType()).isEqualTo("application/problem+json");
    assertThat(third.response().getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");

    assertThat(run(request("POST", "/api/registrations", "2.2.2.2")).passed()).isNotNull();
    assertThat(run(request("GET", "/api/form-config", "1.1.1.1")).passed()).isNotNull();
    now.set(now.get().plusSeconds(60));
    assertThat(run(request("POST", "/api/registrations", "1.1.1.1")).passed()).isNotNull();
  }

  @Test
  void eachGroupHasItsOwnLimit() throws Exception {
    run(request("POST", "/api/organizer/token", "3.3.3.3"));
    run(request("GET", "/api/organizer/registrations/export", "3.3.3.3"));
    run(request("GET", "/api/form-config", "3.3.3.3"));

    assertThat(run(request("POST", "/api/organizer/token", "3.3.3.3")).response().getStatus())
        .isEqualTo(429);
    assertThat(
            run(request("GET", "/api/organizer/registrations/export", "3.3.3.3"))
                .response()
                .getStatus())
        .isEqualTo(429);
    assertThat(run(request("GET", "/api/form-config", "3.3.3.3")).response().getStatus())
        .isEqualTo(429);
  }

  @Test
  void requestsOutsideTheGroupsAreNotCounted() throws Exception {
    for (int i = 0; i < 5; i++) {
      assertThat(run(request("GET", "/api/registrations", "4.4.4.4")).passed()).isNotNull();
    }
    assertThat(RequestLimitsFilter.group(request("PUT", "/api/form-config", "x"))).isNull();
    assertThat(filter.shouldNotFilter(request("GET", "/actuator/health", "x"))).isTrue();
    assertThat(filter.shouldNotFilter(request("GET", "/api/form-config", "x"))).isFalse();
  }

  @Test
  void declaredBodyAboveTheLimitIsRejected() throws Exception {
    MockHttpServletRequest request = request("POST", "/api/registrations", "5.5.5.5");
    request.setContent("01234567890".getBytes(UTF_8));

    Result result = run(request);

    assertThat(result.passed()).isNull();
    assertThat(result.response().getStatus()).isEqualTo(413);
    assertThat(result.response().getContentAsString()).contains("PAYLOAD_TOO_LARGE");
  }

  @Test
  void undeclaredBodyIsReadBoundedAndPassedOn() throws Exception {
    MockHttpServletRequest small = chunked("0123456789");
    Result passed = run(small);
    assertThat(passed.passed()).isNotNull();
    assertThat(new String(passed.passed().getInputStream().readAllBytes(), UTF_8))
        .isEqualTo("0123456789");
    assertThat(passed.passed().getContentLength()).isEqualTo(10);
    assertThat(passed.passed().getContentLengthLong()).isEqualTo(10);
    assertThat(passed.passed().getReader().readLine()).isEqualTo("0123456789");

    Result rejected = run(chunked("0123456789A"));
    assertThat(rejected.passed()).isNull();
    assertThat(rejected.response().getStatus()).isEqualTo(413);
  }

  private static MockHttpServletRequest chunked(String body) {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/organizer/token") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }

          @Override
          public int getContentLength() {
            return -1;
          }
        };
    request.setRemoteAddr("6.6.6." + body.length());
    request.setContent(body.getBytes(UTF_8));
    return request;
  }
}
