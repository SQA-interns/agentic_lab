package si.konferenca.registration.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OrganizerTransportFilterTest {

  private static final String EXPORT = "/api/organizer/registrations/export";

  private record Outcome(int status, boolean passedOn, String body) {}

  private static Outcome call(boolean requireHttps, String path, String remoteAddr, boolean secure)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setRemoteAddr(remoteAddr);
    request.setSecure(secure);
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    new OrganizerTransportFilter(requireHttps).doFilter(request, response, chain);
    return new Outcome(
        response.getStatus(), chain.getRequest() != null, response.getContentAsString());
  }

  @Test
  void plainHttpFromARemoteClientIsRefused() throws Exception {
    Outcome o = call(true, EXPORT, "203.0.113.7", false);

    assertThat(o.status()).isEqualTo(403);
    assertThat(o.passedOn()).isFalse();
    assertThat(o.body()).contains("\"code\":\"HTTPS_REQUIRED\"");
  }

  @Test
  void httpsFromARemoteClientIsAllowed() throws Exception {
    assertThat(call(true, EXPORT, "203.0.113.7", true).passedOn()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"127.0.0.1", "127.1.2.3", "::1", "0:0:0:0:0:0:0:1"})
  void loopbackClientsAreExempt(String address) throws Exception {
    assertThat(call(true, EXPORT, address, false).passedOn()).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "localhost", "evil.example", "10.0.0.1", "172.17.0.1"})
  void nonLoopbackOrNonLiteralAddressesAreNotExempt(String address) throws Exception {
    assertThat(call(true, EXPORT, address, false).status()).isEqualTo(403);
  }

  @Test
  void otherPathsAreNotAffected() throws Exception {
    assertThat(call(true, "/api/registrations", "203.0.113.7", false).passedOn()).isTrue();
  }

  @Test
  void canBeDisabledForLocalPlainHttpSetups() throws Exception {
    assertThat(call(false, EXPORT, "203.0.113.7", false).passedOn()).isTrue();
  }
}
