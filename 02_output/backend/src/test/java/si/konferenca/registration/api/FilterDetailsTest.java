package si.konferenca.registration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ReadListener;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Details of the filters that the first mutation run showed untested (SR-03, SR-06). */
class FilterDetailsTest {

  private static MockHttpServletRequest request(String method, String uri, String address) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRemoteAddr(address);
    return request;
  }

  @Test
  void allowedRequestsReachTheRestOfTheChain() throws Exception {
    RateLimitFilter rateLimit =
        new RateLimitFilter(
            Map.of(RateLimitFilter.Group.REGISTRATIONS, 5),
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    MockFilterChain limited = new MockFilterChain();
    rateLimit.doFilter(
        request("POST", "/api/registrations", "1.1.1.1"), new MockHttpServletResponse(), limited);
    MockFilterChain ungrouped = new MockFilterChain();
    rateLimit.doFilter(
        request("GET", "/actuator/health", "1.1.1.1"), new MockHttpServletResponse(), ungrouped);
    MockFilterChain https = new MockFilterChain();
    new OrganizerHttpsFilter(true)
        .doFilter(request("GET", "/api/export", "127.0.0.1"), new MockHttpServletResponse(), https);

    assertThat(limited.getRequest()).isNotNull();
    assertThat(ungrouped.getRequest()).isNotNull();
    assertThat(https.getRequest()).isNotNull();
  }

  @Test
  void refusedRequestsDoNotReachTheChain() throws Exception {
    MockFilterChain chain = new MockFilterChain();
    new OrganizerHttpsFilter(true)
        .doFilter(
            request("GET", "/api/export", "198.51.100.7"), new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void oldWindowsAreDroppedOnceManyClientsAreTracked() throws Exception {
    Clock[] clock = {Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC)};
    RateLimitFilter filter =
        new RateLimitFilter(
            Map.of(RateLimitFilter.Group.FORM, 100),
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
                return clock[0].instant();
              }
            });
    for (int i = 0; i < 10_000; i++) {
      filter.doFilter(
          request("GET", "/api/registration-form", "10.0." + (i / 250) + "." + (i % 250)),
          new MockHttpServletResponse(),
          new MockFilterChain());
    }
    clock[0] = Clock.fixed(Instant.parse("2026-10-09T08:01:00Z"), ZoneOffset.UTC);
    filter.doFilter(
        request("GET", "/api/registration-form", "10.99.0.1"),
        new MockHttpServletResponse(),
        new MockFilterChain());
    assertThat(filter.trackedWindows()).as("at the bound: no cleanup yet").isEqualTo(10_001);

    filter.doFilter(
        request("GET", "/api/registration-form", "10.99.0.2"),
        new MockHttpServletResponse(),
        new MockFilterChain());
    assertThat(filter.trackedWindows()).as("above the bound: old windows dropped").isEqualTo(2);
  }

  @Test
  void zeroBytesCountTowardsTheLimit() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(2);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/registrations") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    request.setContent(new byte[] {0, 0, 0});

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> {
          var in = req.getInputStream();
          in.read();
          in.read();
          assertThatThrownBy(in::read)
              .isInstanceOf(RequestSizeFilter.PayloadTooLargeException.class);
        });
  }

  @Test
  void exactlyTheLimitIsAllowedInBlockReads() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(4);
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/registrations") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    request.setContent(new byte[] {1, 2, 3, 4});

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> assertThat(req.getInputStream().readAllBytes()).hasSize(4));
  }

  @Test
  void limitedStreamDelegatesItsAsyncState() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(100);
    MockHttpServletRequest request = request("POST", "/api/registrations", "1.1.1.1");
    request.setContent(new byte[] {1});

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (req, res) -> {
          var in = req.getInputStream();
          assertThat(in.isFinished()).isFalse();
          assertThat(in.isReady()).isTrue();
          in.read();
          assertThat(in.read()).isEqualTo(-1);
          assertThat(in.isFinished()).isTrue();
          assertThatThrownBy(
                  () ->
                      in.setReadListener(
                          new ReadListener() {
                            @Override
                            public void onDataAvailable() {}

                            @Override
                            public void onAllDataRead() {}

                            @Override
                            public void onError(Throwable t) {}
                          }))
              .as("the mock stream refuses listeners; the call reached it")
              .isInstanceOf(UnsupportedOperationException.class);
        });
  }

  @Test
  void literalsThatAreNotAddressesAreNotLoopback() {
    assertThat(OrganizerHttpsFilter.isLoopback("127.0.0.1 ")).isFalse();
    assertThat(OrganizerHttpsFilter.isLoopback("1:2:3:4:5:6:7:8:9")).isFalse();
  }
}
