package lab.conference.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Targets surviving mutants in security-relevant platform code (phase 6 fix loop). */
class PlatformEdgeTest {

  private static RequestGuardFilter filter(int perMinute) {
    return new RequestGuardFilter(
        new AppProperties(
            "test",
            null,
            null,
            null,
            null,
            null,
            List.of(),
            new AppProperties.RateLimit(perMinute, perMinute),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1)),
        Clock.systemUTC());
  }

  @Test
  void nonGuardedRequestsReachTheChain() throws Exception {
    MockFilterChain chain = new MockFilterChain();
    filter(1)
        .doFilter(
            new MockHttpServletRequest("GET", "/api/catalog"),
            new MockHttpServletResponse(),
            chain);
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void problemResponsesHaveJsonBodyAndUtf8() throws Exception {
    RequestGuardFilter f = filter(1);
    MockHttpServletRequest first =
        new MockHttpServletRequest("POST", "/api/registrations/external");
    f.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
    MockHttpServletResponse limited = new MockHttpServletResponse();
    f.doFilter(
        new MockHttpServletRequest("POST", "/api/registrations/external"),
        limited,
        new MockFilterChain());
    assertThat(limited.getCharacterEncoding()).isEqualTo("UTF-8");
    assertThat(limited.getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo("{\"type\":\"about:blank\",\"title\":\"Too many requests\",\"status\":429}");
  }

  @Test
  void streamedBodyAtExactLimitIsReadableByteByByte() throws Exception {
    MockHttpServletRequest r =
        new MockHttpServletRequest("POST", "/api/registrations/external") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    byte[] body = new byte[RequestGuardFilter.MAX_BODY_BYTES];
    java.util.Arrays.fill(body, (byte) 'a');
    r.setContent(body);
    MockFilterChain chain = new MockFilterChain();
    filter(100).doFilter(r, new MockHttpServletResponse(), chain);
    jakarta.servlet.ServletInputStream in = chain.getRequest().getInputStream();
    int count = 0;
    int b;
    while ((b = in.read()) >= 0) {
      assertThat(b).isEqualTo('a');
      count++;
    }
    assertThat(count).isEqualTo(RequestGuardFilter.MAX_BODY_BYTES);
    assertThat(in.isFinished()).isTrue();
    assertThat(in.isReady()).isTrue();
  }

  @Test
  void bulkReadAtExactLimitSucceeds() throws Exception {
    MockHttpServletRequest r =
        new MockHttpServletRequest("POST", "/api/registrations/external") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    r.setContent(new byte[RequestGuardFilter.MAX_BODY_BYTES]);
    MockFilterChain chain = new MockFilterChain();
    filter(100).doFilter(r, new MockHttpServletResponse(), chain);
    byte[] buf = new byte[RequestGuardFilter.MAX_BODY_BYTES + 100];
    int total = 0;
    int n;
    InputStream in = chain.getRequest().getInputStream();
    while ((n = in.read(buf, total, buf.length - total)) > 0) {
      total += n;
    }
    assertThat(total).isEqualTo(RequestGuardFilter.MAX_BODY_BYTES);
  }

  @Test
  void organizerBeanRejectsWeakHashes() {
    SecurityConfig config = new SecurityConfig();
    AppProperties weak =
        new AppProperties(
            null,
            null,
            null,
            null,
            null,
            new AppProperties.Organizer(
                "org", "{bcrypt}" + new BCryptPasswordEncoder(4).encode("pw")),
            null,
            null,
            null,
            null,
            null);
    assertThatThrownBy(() -> config.organizer(weak)).hasMessageContaining("cost");
    AppProperties plain =
        new AppProperties(
            null,
            null,
            null,
            null,
            null,
            new AppProperties.Organizer("org", "{noop}pw"),
            null,
            null,
            null,
            null,
            null);
    assertThatThrownBy(() -> config.organizer(plain)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void limiterReportsTheWaitForAFractionalToken() {
    TokenBucketLimiterTest.MutableClock clock = new TokenBucketLimiterTest.MutableClock();
    TokenBucketLimiter limiter = new TokenBucketLimiter(60, clock);
    for (int i = 0; i < 60; i++) {
      assertThat(limiter.tryAcquire("a")).isZero();
    }
    assertThat(limiter.tryAcquire("a")).isEqualTo(1);
    clock.now = clock.now.plusMillis(500);
    assertThat(limiter.tryAcquire("a")).isEqualTo(1);
    clock.now = clock.now.plusMillis(600);
    assertThat(limiter.tryAcquire("a")).isZero();
  }

  @Test
  void pruningDropsOnlyFullBuckets() {
    TokenBucketLimiterTest.MutableClock clock = new TokenBucketLimiterTest.MutableClock();
    TokenBucketLimiter limiter = new TokenBucketLimiter(2, clock);
    limiter.tryAcquire("busy");
    limiter.tryAcquire("busy");
    for (int i = 0; i < 10_001; i++) {
      limiter.tryAcquire("idle" + i);
    }
    clock.now = clock.now.plusMillis(100);
    limiter.tryAcquire("trigger-prune");
    assertThat(limiter.tryAcquire("busy")).as("busy bucket kept its empty state").isPositive();
  }

  @Test
  void clockNanosecondsAreUsed() {
    TokenBucketLimiterTest.MutableClock clock = new TokenBucketLimiterTest.MutableClock();
    TokenBucketLimiter limiter = new TokenBucketLimiter(60, clock);
    for (int i = 0; i < 60; i++) {
      limiter.tryAcquire("a");
    }
    clock.now = clock.now.plusNanos(999_000_000);
    assertThat(limiter.tryAcquire("a")).as("0.999 s is not yet a full token").isPositive();
    clock.now = clock.now.plusNanos(2_000_000);
    assertThat(limiter.tryAcquire("a")).isZero();
  }
}
