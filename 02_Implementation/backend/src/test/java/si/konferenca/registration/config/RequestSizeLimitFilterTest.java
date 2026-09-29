package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletRequest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestSizeLimitFilterTest {

  private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(10);
  private final AtomicReference<String> seenBody = new AtomicReference<>();

  private MockHttpServletResponse call(MockHttpServletRequest request) throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(
        request,
        response,
        (ServletRequest req, jakarta.servlet.ServletResponse res) ->
            seenBody.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8)));
    return response;
  }

  @Test
  void bodyWithinLimitIsReplayedToTheApplication() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/registrations");
    request.setContent("0123456789".getBytes(StandardCharsets.UTF_8));

    assertThat(call(request).getStatus()).isEqualTo(200);
    assertThat(seenBody.get()).isEqualTo("0123456789");
  }

  @Test
  void declaredOversizeIsRejectedWithoutReading() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/registrations");
    request.setContent(new byte[11]);

    MockHttpServletResponse response = call(request);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString()).contains("PAYLOAD_TOO_LARGE");
    assertThat(seenBody.get()).isNull();
  }

  @Test
  void undeclaredOversizeIsDetectedWhileReading() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/registrations") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }

          @Override
          public jakarta.servlet.ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(new byte[100]);
            return new org.springframework.mock.web.DelegatingServletInputStream(in);
          }
        };

    assertThat(call(request).getStatus()).isEqualTo(413);
    assertThat(seenBody.get()).isNull();
  }

  @Test
  void nonApiPathsAreNotFiltered() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/actuator/health");
    request.setContent(new byte[100]);

    assertThat(call(request).getStatus()).isEqualTo(200);
    assertThat(seenBody.get()).hasSize(100);
  }
}
