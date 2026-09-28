package org.example.conference.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.example.conference.shared.web.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** AC-X-03: per-client-IP limit for public submission. */
class RateLimitFilterTest {

  private static MockHttpServletResponse post(RateLimitFilter filter, String ip) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/registrations/x");
    request.setRemoteAddr(ip);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  @Test
  void limitsPerClientAndWindow() throws Exception {
    MutableClock clock = new MutableClock();
    RateLimitFilter filter = new RateLimitFilter(2, new ObjectMapper(), clock);
    assertThat(post(filter, "10.0.0.1").getStatus()).isEqualTo(200);
    assertThat(post(filter, "10.0.0.1").getStatus()).isEqualTo(200);
    MockHttpServletResponse limited = post(filter, "10.0.0.1");
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getContentAsString()).contains("RATE_LIMITED");
    assertThat(post(filter, "10.0.0.2").getStatus()).isEqualTo(200);
    clock.advance(60_001);
    assertThat(post(filter, "10.0.0.1").getStatus()).isEqualTo(200);
  }

  @Test
  void doesNotLimitGetRequests() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(1, new ObjectMapper(), new MutableClock());
    for (int i = 0; i < 3; i++) {
      MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/form-config");
      MockHttpServletResponse response = new MockHttpServletResponse();
      filter.doFilter(request, response, new MockFilterChain());
      assertThat(response.getStatus()).isEqualTo(200);
    }
  }

  static final class MutableClock extends Clock {
    private long millis = 1_000_000;

    void advance(long delta) {
      millis += delta;
    }

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
      return Instant.ofEpochMilli(millis);
    }
  }
}
