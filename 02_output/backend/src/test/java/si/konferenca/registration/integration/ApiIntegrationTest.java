package si.konferenca.registration.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.acceptance.support.AcceptanceTest;
import si.konferenca.registration.acceptance.support.Registrations;
import si.konferenca.registration.acceptance.support.TestStack;
import si.konferenca.registration.application.DuplicateEmailException;
import si.konferenca.registration.application.RegistrationRepository;
import si.konferenca.registration.domain.ParticipantDetails;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.RegistrationType;
import tools.jackson.databind.json.JsonMapper;

/** Request handling, security headers and persistence details through the running application. */
class ApiIntegrationTest extends AcceptanceTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final HttpClient http = HttpClient.newHttpClient();

  @LocalServerPort private int port;
  @Autowired private RegistrationRepository repository;
  @Autowired private PlatformTransactionManager transactionManager;

  private HttpResponse<String> send(HttpRequest.Builder request)
      throws IOException, InterruptedException {
    return http.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  }

  private HttpRequest.Builder post(String body, String contentType) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/registrations"))
        .header("Content-Type", contentType)
        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
  }

  private HttpRequest.Builder export() {
    String basic =
        Base64.getEncoder()
            .encodeToString(
                (TestStack.ORGANIZER_USERNAME + ":" + TestStack.ORGANIZER_PASSWORD)
                    .getBytes(StandardCharsets.UTF_8));
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/export"))
        .header("Authorization", "Basic " + basic)
        .GET();
  }

  @Test
  void malformedJsonUnknownPropertiesAndUnknownTypesAreInvalidRequests() throws Exception {
    var withUnknown = Registrations.external();
    withUnknown.put("isAdmin", true);
    var unknownType = Registrations.external();
    unknownType.put("type", "VIP");
    var noType = Registrations.external();
    noType.remove("type");

    for (String body :
        List.of(
            "{not json",
            JSON.writeValueAsString(withUnknown),
            JSON.writeValueAsString(unknownType),
            JSON.writeValueAsString(noType),
            "null")) {
      HttpResponse<String> response = send(post(body, "application/json"));
      assertThat(response.statusCode()).as(body).isEqualTo(400);
      assertThat(response.body()).contains("\"error\":\"invalid_request\"");
    }
    assertNothingStoredOrSent();
  }

  @Test
  void tooManySelectionsAreAnInvalidRequest() throws Exception {
    var body = Registrations.external();
    var ids = body.putArray("optionIds");
    for (int i = 0; i < 51; i++) {
      ids.add("o" + i);
    }

    HttpResponse<String> response = send(post(JSON.writeValueAsString(body), "application/json"));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("invalid_request");
  }

  @Test
  void fieldOfTheOtherTypeIsReportedAsNotAllowed() throws Exception {
    var body = Registrations.external();
    body.put("studentId", "123");

    HttpResponse<String> response = send(post(JSON.writeValueAsString(body), "application/json"));

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("\"field\":\"studentId\"").contains("not_allowed");
  }

  @Test
  void wrongContentTypeIsRefused() throws Exception {
    HttpResponse<String> response = send(post("type=EXTERNAL", "text/plain"));

    assertThat(response.statusCode()).isEqualTo(415);
    assertThat(response.body()).contains("unsupported_media_type");
  }

  @Test
  void oversizedBodyIsRefused() throws Exception {
    var body = Registrations.external();
    body.put("organization", "x".repeat(20_000));

    HttpResponse<String> response = send(post(JSON.writeValueAsString(body), "application/json"));

    assertThat(response.statusCode()).isEqualTo(413);
    assertThat(response.body()).contains("payload_too_large");
    assertThat(db.countRegistrations()).isZero();
  }

  @Test
  void apiResponsesCarrySecurityHeaders() throws Exception {
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/registration-form")));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Security-Policy"))
        .hasValue("default-src 'none'; frame-ancestors 'none'");
    assertThat(response.headers().firstValue("X-Frame-Options")).hasValue("DENY");
    assertThat(response.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");
    assertThat(response.headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
  }

  @Test
  void unknownPathsAreDeniedWithJson() throws Exception {
    HttpResponse<String> response =
        send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/admin")));

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.body()).contains("\"error\":\"unauthorized\"");
    assertThat(response.headers().firstValue("WWW-Authenticate"))
        .hasValue("Basic realm=\"organizer\"");
  }

  @Test
  void readinessIsPublicAndIncludesTheDatabase() throws Exception {
    HttpResponse<String> response =
        send(
            HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness")));

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("\"status\":\"UP\"");
  }

  @Test
  void organizerAccessOverPlainHttpThroughAProxyIsRefused() throws Exception {
    HttpResponse<String> plain = send(export().header("X-Forwarded-For", "203.0.113.5"));
    HttpResponse<String> https =
        send(
            export().header("X-Forwarded-For", "203.0.113.5").header("X-Forwarded-Proto", "https"));

    assertThat(plain.statusCode()).isEqualTo(403);
    assertThat(plain.body()).contains("https_required");
    assertThat(https.statusCode()).isEqualTo(200);
  }

  @Test
  void databaseRejectsADuplicateEmailEvenWithoutTheEarlyCheck() {
    TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    transactions.executeWithoutResult(status -> repository.insert(registration("Race@Example.si")));

    assertThatThrownBy(
            () ->
                transactions.executeWithoutResult(
                    status -> repository.insert(registration("race@example.SI"))))
        .isInstanceOf(DuplicateEmailException.class);
    assertThat(db.countRegistrations()).isEqualTo(1);
  }

  @Test
  void registrationsAreListedOldestFirstWithOptionsAndConsents() {
    TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    transactions.executeWithoutResult(status -> repository.insert(registration("b@example.si")));
    transactions.executeWithoutResult(status -> repository.insert(registration("a@example.si")));

    List<Registration> all = transactions.execute(status -> repository.findAllOldestFirst());

    assertThat(all)
        .extracting(r -> r.participant().email())
        .containsExactly("b@example.si", "a@example.si");
    assertThat(all.get(0).consents()).hasSize(1);
  }

  private static Registration registration(String email) {
    Instant at = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    try {
      Thread.sleep(2);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return new Registration(
        UUID.randomUUID(),
        RegistrationType.EXTERNAL,
        new ParticipantDetails("A", "B", email, "Org", null, null, null),
        at,
        List.of(),
        List.of(new si.konferenca.registration.domain.GivenConsent("data-processing", "t", at)));
  }
}
