package lab.conference.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestGuardFilterTest {

  private static AppProperties props(int registration, int export) {
    return new AppProperties(
        "test",
        null,
        null,
        null,
        null,
        null,
        List.of("https://conference.example.test"),
        new AppProperties.RateLimit(registration, export),
        Duration.ofSeconds(1),
        Duration.ofSeconds(1),
        Duration.ofSeconds(1));
  }

  private static MockHttpServletRequest post(String path) {
    MockHttpServletRequest r = new MockHttpServletRequest("POST", path);
    r.setRemoteAddr("10.0.0.1");
    r.setContent("{}".getBytes());
    return r;
  }

  private static MockHttpServletResponse run(
      RequestGuardFilter f, MockHttpServletRequest r, MockFilterChain chain) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    f.doFilter(r, response, chain);
    return response;
  }

  @Test
  void registrationPostsAreRateLimitedPerAddress() throws Exception {
    RequestGuardFilter f = new RequestGuardFilter(props(2, 10), Clock.systemUTC());
    assertThat(run(f, post("/api/registrations/external"), new MockFilterChain()).getStatus())
        .isEqualTo(200);
    assertThat(run(f, post("/api/registrations/student"), new MockFilterChain()).getStatus())
        .isEqualTo(200);
    MockFilterChain chain = new MockFilterChain();
    MockHttpServletResponse limited = run(f, post("/api/registrations/external"), chain);
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isNotBlank();
    assertThat(limited.getContentType()).startsWith("application/problem+json");
    assertThat(chain.getRequest()).isNull();
    MockHttpServletRequest other = post("/api/registrations/external");
    other.setRemoteAddr("10.0.0.2");
    assertThat(run(f, other, new MockFilterChain()).getStatus()).isEqualTo(200);
  }

  @Test
  void catalogAndGetRequestsAreNotLimited() throws Exception {
    RequestGuardFilter f = new RequestGuardFilter(props(1, 1), Clock.systemUTC());
    for (int i = 0; i < 5; i++) {
      MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/catalog");
      assertThat(run(f, get, new MockFilterChain()).getStatus()).isEqualTo(200);
      MockHttpServletRequest getRegistrations =
          new MockHttpServletRequest("GET", "/api/registrations/x");
      assertThat(run(f, getRegistrations, new MockFilterChain()).getStatus()).isEqualTo(200);
    }
  }

  @Test
  void organizerRequestsUseTheExportLimit() throws Exception {
    RequestGuardFilter f = new RequestGuardFilter(props(100, 1), Clock.systemUTC());
    MockHttpServletRequest first = new MockHttpServletRequest("GET", "/api/organizer/export.xlsx");
    assertThat(run(f, first, new MockFilterChain()).getStatus()).isEqualTo(200);
    MockHttpServletRequest second = new MockHttpServletRequest("GET", "/api/organizer/export.xlsx");
    assertThat(run(f, second, new MockFilterChain()).getStatus()).isEqualTo(429);
  }

  @Test
  void foreignOriginIsForbiddenAllowedOriginAndNoOriginPass() throws Exception {
    RequestGuardFilter f = new RequestGuardFilter(props(100, 10), Clock.systemUTC());
    MockHttpServletRequest evil = post("/api/registrations/external");
    evil.addHeader("Origin", "https://evil.test");
    assertThat(run(f, evil, new MockFilterChain()).getStatus()).isEqualTo(403);
    MockHttpServletRequest ok = post("/api/registrations/external");
    ok.addHeader("Origin", "https://conference.example.test");
    assertThat(run(f, ok, new MockFilterChain()).getStatus()).isEqualTo(200);
  }

  /** A request without Content-Length (chunked) whose body exceeds the limit. */
  private static InputStream streamedRequestBody(RequestGuardFilter f) throws Exception {
    MockHttpServletRequest chunked =
        new MockHttpServletRequest("POST", "/api/registrations/external") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    chunked.setRemoteAddr("10.9.9.9");
    chunked.setContent(new byte[RequestGuardFilter.MAX_BODY_BYTES + 10]);
    MockFilterChain chain = new MockFilterChain();
    run(f, chunked, chain);
    return chain.getRequest().getInputStream();
  }

  @Test
  void declaredOversizedBodyIsRejectedAndStreamedBodyIsCapped() throws Exception {
    RequestGuardFilter f = new RequestGuardFilter(props(100, 10), Clock.systemUTC());
    MockHttpServletRequest big = post("/api/registrations/external");
    big.setContent(new byte[RequestGuardFilter.MAX_BODY_BYTES + 1]);
    assertThat(run(f, big, new MockFilterChain()).getStatus()).isEqualTo(413);

    MockHttpServletRequest exact = post("/api/registrations/external");
    exact.setContent(new byte[RequestGuardFilter.MAX_BODY_BYTES]);
    MockFilterChain chain = new MockFilterChain();
    run(f, exact, chain);
    try (InputStream in = chain.getRequest().getInputStream()) {
      assertThat(in.readAllBytes()).hasSize(RequestGuardFilter.MAX_BODY_BYTES);
    }

    InputStream bulk = streamedRequestBody(f);
    assertThatThrownBy(bulk::readAllBytes).isInstanceOf(PayloadTooLargeException.class);
    InputStream single = streamedRequestBody(f);
    assertThatThrownBy(
            () -> {
              while (single.read() >= 0) {
                // consume byte by byte
              }
            })
        .isInstanceOf(PayloadTooLargeException.class);
  }
}
