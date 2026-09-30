package si.konferenca.registration.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import si.konferenca.registration.application.DuplicateRegistrationException;
import si.konferenca.registration.application.ValidationException;
import si.konferenca.registration.settings.AppProperties;

class WebLayerTest {

  private static final class MutableClock extends Clock {
    private Instant now = Instant.parse("2026-09-30T10:00:00Z");

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
      return now;
    }

    void advanceSeconds(long s) {
      now = now.plusSeconds(s);
    }
  }

  private static MockHttpServletRequest request(String method, String uri, String ip) {
    MockHttpServletRequest r = new MockHttpServletRequest(method, uri);
    r.setRemoteAddr(ip);
    return r;
  }

  private static final class RecordingChain implements FilterChain {
    ServletRequest passed;

    @Override
    public void doFilter(ServletRequest request, jakarta.servlet.ServletResponse response) {
      passed = request;
    }
  }

  @Test
  void rateLimitCountsPerClientAndBucketAndResetsAfterAMinute() throws Exception {
    MutableClock clock = new MutableClock();
    RateLimitFilter filter = new RateLimitFilter(new AppProperties.RateLimit(2, 1, 3), clock);

    assertThat(filter.acquire("registration|a", 2)).isZero();
    assertThat(filter.acquire("registration|a", 2)).isZero();
    assertThat(filter.acquire("registration|a", 2)).isEqualTo(60_000);
    assertThat(filter.acquire("registration|b", 2)).isZero();
    clock.advanceSeconds(59);
    assertThat(filter.acquire("registration|a", 2)).isEqualTo(1_000);
    clock.advanceSeconds(1);
    assertThat(filter.acquire("registration|a", 2)).isZero();
  }

  @Test
  void rateLimitFilterAnswers429WithRetryAfter() throws Exception {
    RateLimitFilter filter =
        new RateLimitFilter(new AppProperties.RateLimit(10, 1, 10), new MutableClock());
    RecordingChain first = new RecordingChain();
    filter.doFilter(
        request("GET", "/api/organizer/x", "1.1.1.1"), new MockHttpServletResponse(), first);
    MockHttpServletResponse second = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request("GET", "/api/organizer/x", "1.1.1.1"), second, chain);

    assertThat(first.passed).isNotNull();
    assertThat(chain.passed).isNull();
    assertThat(second.getStatus()).isEqualTo(429);
    assertThat(second.getHeader("Retry-After")).isEqualTo("60");
    assertThat(second.getContentAsString()).contains("\"error\":\"rate_limited\"");
  }

  @Test
  void rateLimitBucketsByPath() {
    assertThat(RateLimitFilter.bucket(request("GET", "/api/organizer/registrations.xlsx", "x")))
        .isEqualTo("export");
    assertThat(RateLimitFilter.bucket(request("POST", "/api/registrations", "x")))
        .isEqualTo("registration");
    assertThat(RateLimitFilter.bucket(request("GET", "/api/options", "x"))).isEqualTo("read");
    assertThat(RateLimitFilter.bucket(request("GET", "/api/config", "x"))).isEqualTo("read");
    assertThat(RateLimitFilter.bucket(request("GET", "/api/health", "x"))).isNull();
  }

  @Test
  void unlimitedPathsPassThrough() throws Exception {
    RateLimitFilter filter =
        new RateLimitFilter(new AppProperties.RateLimit(0, 0, 0), new MutableClock());
    RecordingChain chain = new RecordingChain();

    filter.doFilter(request("GET", "/api/health", "1.1.1.1"), new MockHttpServletResponse(), chain);

    assertThat(chain.passed).isNotNull();
  }

  @Test
  void requestSizeFilterRejectsDeclaredLengthOverLimit() throws Exception {
    MockHttpServletRequest r = request("POST", "/api/registrations", "1.1.1.1");
    r.setContent(new byte[11]);
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    new RequestSizeFilter(10).doFilter(r, response, chain);

    assertThat(chain.passed).isNull();
    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString()).contains("payload_too_large");
  }

  @Test
  void requestSizeFilterLimitsBodiesWithoutLength() throws Exception {
    MockHttpServletRequest r =
        new MockHttpServletRequest("POST", "/api/registrations") {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    r.setContent(new byte[20]);
    RecordingChain chain = new RecordingChain();

    new RequestSizeFilter(10).doFilter(r, new MockHttpServletResponse(), chain);

    var in = chain.passed.getInputStream();
    assertThat(in.read(new byte[10], 0, 10)).isEqualTo(10);
    assertThatThrownBy(in::read).isInstanceOf(RequestSizeFilter.PayloadTooLargeException.class);
  }

  @Test
  void requestSizeFilterPassesSmallBodies() throws Exception {
    MockHttpServletRequest r = request("POST", "/api/registrations", "1.1.1.1");
    r.setContent(new byte[] {1, 2, 3});
    RecordingChain chain = new RecordingChain();

    new RequestSizeFilter(3).doFilter(r, new MockHttpServletResponse(), chain);

    var in = chain.passed.getInputStream();
    assertThat(in.read()).isEqualTo(1);
    assertThat(in.read(new byte[5], 0, 5)).isEqualTo(2);
    assertThat(in.read()).isEqualTo(-1);
    assertThat(in.isFinished()).isTrue();
    assertThat(chain.passed.getInputStream()).isSameAs(in);
  }

  @Test
  void httpsFilterRefusesPlainHttpOrganizerRequests() throws Exception {
    OrganizerHttpsFilter filter = new OrganizerHttpsFilter();
    MockHttpServletResponse response = new MockHttpServletResponse();
    RecordingChain chain = new RecordingChain();

    filter.doFilter(
        request("GET", "/api/organizer/registrations.xlsx", "1.1.1.1"), response, chain);

    assertThat(chain.passed).isNull();
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString()).contains("\"error\":\"forbidden\"");
  }

  @Test
  void httpsFilterPassesSecureAndOtherRequests() throws Exception {
    OrganizerHttpsFilter filter = new OrganizerHttpsFilter();
    MockHttpServletRequest secure = request("GET", "/api/organizer/registrations.xlsx", "x");
    secure.setSecure(true);
    RecordingChain a = new RecordingChain();
    RecordingChain b = new RecordingChain();

    filter.doFilter(secure, new MockHttpServletResponse(), a);
    filter.doFilter(request("GET", "/api/options", "x"), new MockHttpServletResponse(), b);

    assertThat(a.passed).isNotNull();
    assertThat(b.passed).isNotNull();
  }

  @Test
  void exceptionHandlerMapsErrorsWithoutInternals() {
    ApiExceptionHandler handler = new ApiExceptionHandler();

    ResponseEntity<ErrorBody> invalid =
        handler.invalid(
            new ValidationException(
                List.of(new ValidationException.FieldViolation("email", "Enter a valid email."))));
    assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(invalid.getBody().fieldErrors())
        .containsExactly(new ErrorBody.FieldErrorBody("email", "Enter a valid email."));

    ResponseEntity<ErrorBody> duplicate = handler.duplicate();
    assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(duplicate.getBody().error()).isEqualTo("duplicate_registration");
    assertThat(duplicate.getBody().fieldErrors())
        .extracting(ErrorBody.FieldErrorBody::field)
        .containsExactly("email");
    assertThat(new DuplicateRegistrationException()).hasMessage("Duplicate registration");

    ResponseEntity<ErrorBody> internal =
        handler.unexpected(new IllegalStateException("SELECT * FROM secret"));
    assertThat(internal.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(internal.getBody().message()).isEqualTo(ApiExceptionHandler.GENERIC_MESSAGE);
    assertThat(internal.getBody().toString()).doesNotContain("SELECT");

    assertThat(handler.mediaType().getStatusCode().value()).isEqualTo(415);
    assertThat(handler.notFound().getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void unreadableBodyIs400UnlessItWasTooLarge() {
    ApiExceptionHandler handler = new ApiExceptionHandler();
    MockHttpInputMessage input = new MockHttpInputMessage(new byte[0]);

    ResponseEntity<ErrorBody> malformed =
        handler.unreadable(new HttpMessageNotReadableException("bad", input));
    ResponseEntity<ErrorBody> tooLarge =
        handler.unreadable(
            new HttpMessageNotReadableException(
                "bad", new IOException(new RequestSizeFilter.PayloadTooLargeException()), input));

    assertThat(malformed.getStatusCode().value()).isEqualTo(400);
    assertThat(malformed.getBody().error()).isEqualTo("validation_failed");
    assertThat(tooLarge.getStatusCode().value()).isEqualTo(413);
  }

  @Test
  void clientConfigNeverExposesTheSecretAndHidesSiteKeyInTestMode() {
    AppProperties production = properties(false, "site", "secret");
    AppProperties testMode = properties(true, "site", "secret");

    ClientConfigController.ClientConfig prod = new ClientConfigController(production).config();
    ClientConfigController.ClientConfig test = new ClientConfigController(testMode).config();

    assertThat(prod).isEqualTo(new ClientConfigController.ClientConfig("site", false, "Konf"));
    assertThat(test).isEqualTo(new ClientConfigController.ClientConfig("", true, "Konf"));
    assertThat(new ClientConfigController(properties(false, null, "s")).config().recaptchaSiteKey())
        .isEmpty();
  }

  @Test
  void errorResponsesWriteJson() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> type = new AtomicReference<>();

    ErrorResponses.write(response, ErrorBody.of(418, "x", "y"));
    type.set(response.getContentType());

    assertThat(response.getStatus()).isEqualTo(418);
    assertThat(type.get()).startsWith("application/json");
    assertThat(response.getContentAsString())
        .isEqualTo("{\"status\":418,\"error\":\"x\",\"message\":\"y\",\"fieldErrors\":[]}");
  }

  private static AppProperties properties(boolean testMode, String siteKey, String secret) {
    return new AppProperties(
        "Konf",
        new AppProperties.Mail("f@x.si"),
        "classpath:x",
        "dir",
        new AppProperties.Recaptcha(testMode, siteKey, secret, "http://v"),
        new AppProperties.Organizer("u", "p", List.of("o@x.si"), true),
        new AppProperties.Cors(List.of()),
        new AppProperties.RateLimit(1, 1, 1),
        10);
  }
}
