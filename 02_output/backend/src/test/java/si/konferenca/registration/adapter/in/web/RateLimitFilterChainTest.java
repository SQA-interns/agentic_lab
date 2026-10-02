package si.konferenca.registration.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** What the rate limit lets through, and that its memory stays bounded (SR-03). */
class RateLimitFilterChainTest {

  private static final Instant START = Instant.parse("2026-01-01T10:00:05Z");

  private static MockFilterChain send(RateLimitFilter filter, String client) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/options");
    request.setRemoteAddr(client);
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, new MockHttpServletResponse(), chain);
    return chain;
  }

  @Test
  void sr03_allowedRequestReachesTheApplicationAndRefusedRequestDoesNot() throws Exception {
    RateLimitFilter filter =
        new RateLimitFilter(
            new RateLimitFilter.Limits(1, 1, 1),
            new ClientRequests(false),
            Clock.fixed(START, ZoneOffset.UTC));

    assertThat(send(filter, "10.0.0.1").getRequest()).as("first request passed on").isNotNull();
    assertThat(send(filter, "10.0.0.1").getRequest()).as("second request stopped").isNull();
  }

  @Test
  void sr03_windowsOfPastMinutesAreDroppedWhenTooManyClientsAreTracked() throws Exception {
    Instant[] now = {START};
    Clock clock =
        new Clock() {
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
            return now[0];
          }
        };
    RateLimitFilter filter =
        new RateLimitFilter(
            new RateLimitFilter.Limits(1, 1, 1), new ClientRequests(false), clock, 3);

    for (String client : new String[] {"10.0.0.1", "10.0.0.2", "10.0.0.3", "10.0.0.4"}) {
      send(filter, client);
    }
    // Four windows are tracked, one more than the bound, but all belong to the current minute.
    send(filter, "10.0.0.5");
    assertThat(filter.trackedWindows()).isEqualTo(5);
    assertThat(send(filter, "10.0.0.1").getRequest()).as("still limited this minute").isNull();

    now[0] = START.plusSeconds(60);
    send(filter, "10.0.0.6");

    assertThat(filter.trackedWindows()).isEqualTo(1);
    assertThat(send(filter, "10.0.0.1").getRequest()).as("allowed again").isNotNull();
  }

  @Test
  void sr03_nothingIsDroppedWhileTheBoundIsNotExceeded() throws Exception {
    RateLimitFilter filter =
        new RateLimitFilter(
            new RateLimitFilter.Limits(1, 1, 1),
            new ClientRequests(false),
            Clock.fixed(START, ZoneOffset.UTC),
            3);

    for (String client : new String[] {"10.0.0.1", "10.0.0.2", "10.0.0.3"}) {
      send(filter, client);
    }
    send(filter, "10.0.0.3");

    assertThat(filter.trackedWindows()).isEqualTo(3);
  }
}
