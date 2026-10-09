package si.konferenca.registration.api;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class FiltersTest {

  private static MockHttpServletRequest post(String path, String remote) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr(remote);
    return request;
  }

  private static MockHttpServletResponse run(
      jakarta.servlet.Filter filter, MockHttpServletRequest request)
      throws ServletException, IOException {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  @Test
  void sr03_registrationsAreLimitedPerClientAndWindow() throws Exception {
    AtomicLong now = new AtomicLong(1_000_000);
    RateLimitFilter filter = new RateLimitFilter(2, 1, 5, now::get);

    assertThat(run(filter, post("/api/registrations", "10.0.0.1")).getStatus()).isEqualTo(200);
    assertThat(run(filter, post("/api/registrations", "10.0.0.1")).getStatus()).isEqualTo(200);
    MockHttpServletResponse limited = run(filter, post("/api/registrations", "10.0.0.1"));
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
    assertThat(limited.getContentType()).isEqualTo("application/problem+json");
    assertThat(run(filter, post("/api/registrations", "10.0.0.2")).getStatus()).isEqualTo(200);

    now.addAndGet(60_000);
    assertThat(run(filter, post("/api/registrations", "10.0.0.1")).getStatus()).isEqualTo(200);
  }

  @Test
  void sr03_exportAndFormsHaveTheirOwnLimits() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(1, 1, 1, () -> 0L);
    MockHttpServletRequest export = new MockHttpServletRequest("GET", "/api/registrations/export");
    export.setRemoteAddr("10.0.0.1");
    MockHttpServletRequest form =
        new MockHttpServletRequest("GET", "/api/registration-form/student");
    form.setRemoteAddr("10.0.0.1");

    assertThat(run(filter, export).getStatus()).isEqualTo(200);
    assertThat(run(filter, form).getStatus()).isEqualTo(200);
    assertThat(run(filter, export).getStatus()).isEqualTo(429);
    assertThat(run(filter, new MockHttpServletRequest("GET", "/actuator/health")).getStatus())
        .isEqualTo(200);
  }

  @Test
  void sr03_bodiesAboveTheLimitAreRefused() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(10);
    MockHttpServletRequest declared = post("/api/registrations", "10.0.0.1");
    declared.setContent("0123456789X".getBytes(StandardCharsets.UTF_8));

    assertThat(run(filter, declared).getStatus()).isEqualTo(413);
  }

  @Test
  void sr03_bodiesWithoutLengthAreCountedToo() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(10);
    MockHttpServletRequest chunked =
        new MockHttpServletRequest("POST", "/api/registrations") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    chunked.setContent("0123456789X".getBytes(StandardCharsets.UTF_8));

    assertThat(run(filter, chunked).getStatus()).isEqualTo(413);
  }

  @Test
  void bodyWithinTheLimitReachesTheHandlerUnchanged() throws Exception {
    RequestSizeFilter filter = new RequestSizeFilter(10);
    MockHttpServletRequest request = post("/api/registrations", "10.0.0.1");
    request.setContent("0123456789".getBytes(StandardCharsets.UTF_8));
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(chain.getRequest().getInputStream().readAllBytes())
        .isEqualTo("0123456789".getBytes(StandardCharsets.UTF_8));
    assertThat(chain.getRequest().getContentLength()).isEqualTo(10);
  }

  @Test
  void sr06_organizerCredentialsNeedHttpsOrLocalhost() throws Exception {
    OrganizerHttpsFilter filter = new OrganizerHttpsFilter();
    MockHttpServletRequest remotePlain =
        new MockHttpServletRequest("GET", "/api/registrations/export");
    remotePlain.setRemoteAddr("203.0.113.5");
    MockHttpServletRequest remoteTls =
        new MockHttpServletRequest("GET", "/api/registrations/export");
    remoteTls.setRemoteAddr("203.0.113.5");
    remoteTls.setSecure(true);
    MockHttpServletRequest local = new MockHttpServletRequest("GET", "/api/registrations/export");
    local.setRemoteAddr("::1");
    MockHttpServletRequest other = new MockHttpServletRequest("GET", "/api/registration-form/x");
    other.setRemoteAddr("203.0.113.5");

    assertThat(run(filter, remotePlain).getStatus()).isEqualTo(403);
    assertThat(run(filter, remoteTls).getStatus()).isEqualTo(200);
    assertThat(run(filter, local).getStatus()).isEqualTo(200);
    assertThat(run(filter, other).getStatus()).isEqualTo(200);
  }

  @Test
  void es07_loggedCauseChainHasClassNamesOnly() {
    RuntimeException e =
        new RuntimeException("Key (email)=(ana@example.si) exists", new IOException("disk /x"));

    assertThat(ApiExceptionHandler.causeChain(e))
        .isEqualTo("java.lang.RuntimeException <- java.io.IOException")
        .doesNotContain("ana@example.si");
  }
}
