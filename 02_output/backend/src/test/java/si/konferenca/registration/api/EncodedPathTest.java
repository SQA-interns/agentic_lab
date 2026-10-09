package si.konferenca.registration.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** F-02: filters match the decoded path, so encoded variants cannot skip them. */
class EncodedPathTest {

  private static MockHttpServletRequest request(String method, String rawUri) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, rawUri);
    request.setRemoteAddr("203.0.113.9");
    return request;
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/%65xport", "/api/export;jsessionid=x", "/%61pi/export"})
  void encodedExportPathsAreStillRateLimitedAndHttpsOnly(String rawUri) throws Exception {
    RateLimitFilter rateLimit =
        new RateLimitFilter(
            Map.of(RateLimitFilter.Group.EXPORTS, 1), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    rateLimit.doFilter(
        request("GET", rawUri), new MockHttpServletResponse(), new MockFilterChain());
    MockHttpServletResponse second = new MockHttpServletResponse();
    rateLimit.doFilter(request("GET", rawUri), second, new MockFilterChain());

    MockHttpServletResponse https = new MockHttpServletResponse();
    new OrganizerHttpsFilter(true).doFilter(request("GET", rawUri), https, new MockFilterChain());

    assertThat(second.getStatus()).isEqualTo(429);
    assertThat(https.getStatus()).isEqualTo(403);
  }

  @Test
  void encodedApiPrefixIsStillSizeLimited() throws Exception {
    MockHttpServletRequest request = request("POST", "/%61pi/registrations");
    request.setContent(new byte[20]);
    MockHttpServletResponse response = new MockHttpServletResponse();

    new RequestSizeFilter(10).doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(413);
  }

  @Test
  void decodedPathIgnoresTheContextPath() {
    MockHttpServletRequest request = request("GET", "/app/api/%65xport");
    request.setContextPath("/app");

    assertThat(RequestPath.of(request)).isEqualTo("/api/export");
  }
}
