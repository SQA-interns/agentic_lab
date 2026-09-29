package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

  private final AtomicLong now = new AtomicLong(1_000_000);
  private final Clock clock =
      new Clock() {
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
          return Instant.ofEpochMilli(now.get());
        }
      };

  private final RateLimitFilter filter = new RateLimitFilter(2, 1, clock);

  /** Response status, or 0 when the request was passed on to the rest of the chain. */
  private MockHttpServletResponse call(String method, String path, String ip)
      throws IOException, jakarta.servlet.ServletException {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setRemoteAddr(ip);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    filter.doFilter(request, response, chain);
    if (response.getStatus() == 200) {
      assertThat(chain.getRequest()).as("allowed request is passed on").isNotNull();
    } else {
      assertThat(chain.getRequest()).as("limited request is not passed on").isNull();
    }
    return response;
  }

  @Test
  void registrationsAreLimitedPerIpAndWindow() throws Exception {
    assertThat(call("POST", "/api/registrations", "1.1.1.1").getStatus()).isEqualTo(200);
    assertThat(call("POST", "/api/registrations", "1.1.1.1").getStatus()).isEqualTo(200);
    MockHttpServletResponse limited = call("POST", "/api/registrations", "1.1.1.1");

    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
    assertThat(limited.getContentType()).startsWith("application/problem+json");
    assertThat(limited.getContentAsString()).contains("\"code\":\"RATE_LIMITED\"");
    assertThat(call("POST", "/api/registrations", "2.2.2.2").getStatus())
        .as("other clients keep their own budget")
        .isEqualTo(200);

    now.addAndGet(RateLimitFilter.WINDOW_MILLIS);
    assertThat(call("POST", "/api/registrations", "1.1.1.1").getStatus()).isEqualTo(200);
  }

  @Test
  void organizerBudgetIsSeparateFromRegistrations() throws Exception {
    call("POST", "/api/registrations", "3.3.3.3");
    call("POST", "/api/registrations", "3.3.3.3");

    assertThat(call("GET", "/api/organizer/registrations/export", "3.3.3.3").getStatus())
        .isEqualTo(200);
    assertThat(call("GET", "/api/organizer/registrations/export", "3.3.3.3").getStatus())
        .isEqualTo(429);
  }

  @Test
  void otherPathsAreNotLimited() throws Exception {
    for (int i = 0; i < 10; i++) {
      assertThat(call("GET", "/api/options", "4.4.4.4").getStatus()).isEqualTo(200);
      assertThat(call("GET", "/api/registrations", "4.4.4.4").getStatus()).isEqualTo(200);
    }
  }

  @Test
  void retryAfterCountsDownWithinTheWindow() {
    filter.consume("k", 1);
    now.addAndGet(45_500);

    assertThat(filter.consume("k", 1)).isEqualTo(15);
  }

  @Test
  void expiredWindowsAreEvictedButLiveOnesKeepCounting() {
    filter.consume("expiring", 1);
    now.addAndGet(RateLimitFilter.WINDOW_MILLIS - 1);
    // One more key than the bound: "expiring" plus MAX_TRACKED_KEYS fresh keys.
    for (int i = 0; i < RateLimitFilter.MAX_TRACKED_KEYS; i++) {
      filter.consume("fresh-" + i, 1);
    }
    now.addAndGet(1);

    // The next call evicts only the expired window; the fresh ones keep their count.
    assertThat(filter.consume("fresh-0", 1)).isPositive();
    assertThat(filter.consume("expiring", 1)).isZero();
  }

  @Test
  void mapIsClearedWhenNothingCanBeEvicted() {
    for (int i = 0; i <= RateLimitFilter.MAX_TRACKED_KEYS + 1; i++) {
      filter.consume("key-" + i, 1);
    }

    // Every window is live, so the bound forces a full reset: the next request is allowed again.
    assertThat(filter.consume("key-0", 1)).isZero();
  }
}
