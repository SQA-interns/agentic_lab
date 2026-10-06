package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OrganizerHttpsFilterTest {

  private static int call(boolean httpsOnly, String path, boolean secure, AtomicInteger passed)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setSecure(secure);
    MockHttpServletResponse response = new MockHttpServletResponse();
    new OrganizerHttpsFilter(httpsOnly)
        .doFilter(request, response, (req, res) -> passed.incrementAndGet());
    return response.getStatus();
  }

  @Test
  void plainHttpExportIsForbiddenWhenHttpsOnly() throws Exception {
    AtomicInteger passed = new AtomicInteger();
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", OrganizerHttpsFilter.EXPORT_PATH);
    MockHttpServletResponse response = new MockHttpServletResponse();

    new OrganizerHttpsFilter(true)
        .doFilter(request, response, (req, res) -> passed.incrementAndGet());

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentType()).isEqualTo("application/problem+json");
    assertThat(response.getContentAsString()).contains("HTTPS required");
    assertThat(passed).hasValue(0);
  }

  @Test
  void secureExportOtherPathsAndLocalPlainHttpPass() throws Exception {
    AtomicInteger passed = new AtomicInteger();

    assertThat(call(true, OrganizerHttpsFilter.EXPORT_PATH, true, passed)).isEqualTo(200);
    assertThat(call(true, "/api/form-config", false, passed)).isEqualTo(200);
    assertThat(call(false, OrganizerHttpsFilter.EXPORT_PATH, false, passed)).isEqualTo(200);
    assertThat(passed).hasValue(3);
  }
}
