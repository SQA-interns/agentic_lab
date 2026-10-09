package si.konferenca.registration.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Black-box client for the registration API, the Mailpit API and the exported workbook. */
public final class Api {

  public static final JsonMapper JSON = JsonMapper.builder().build();
  public static final String EXCEL =
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  public static final String TEST_CAPTCHA_TOKEN = "test-pass";

  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  private Api() {}

  /** HTTP response with its body as bytes. */
  public record Response(int status, String contentType, byte[] body, HttpResponse<byte[]> raw) {
    public String text() {
      return new String(body, StandardCharsets.UTF_8);
    }

    public JsonNode json() {
      return JSON.readTree(text());
    }

    public String header(String name) {
      return raw.headers().firstValue(name).orElse(null);
    }
  }

  public static Response get(String url) {
    return send(HttpRequest.newBuilder(URI.create(url)).GET());
  }

  public static Response getWithBasic(String url, String user, String password) {
    String token =
        Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    return send(
        HttpRequest.newBuilder(URI.create(url)).header("Authorization", "Basic " + token).GET());
  }

  public static Response postJson(String url, Object body) {
    String json = body instanceof String s ? s : JSON.writeValueAsString(body);
    return send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)));
  }

  private static Response send(HttpRequest.Builder request) {
    try {
      HttpResponse<byte[]> response =
          HTTP.send(
              request.timeout(Duration.ofSeconds(30)).build(),
              HttpResponse.BodyHandlers.ofByteArray());
      return new Response(
          response.statusCode(),
          response.headers().firstValue("Content-Type").orElse(""),
          response.body(),
          response);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** The form definition of {@code type} ("external" or "student"); asserts 200. */
  public static JsonNode form(Backend backend, String type) {
    Response response = get(backend.url("/api/registration-form/" + type));
    assertThat(response.status()).as("form status, body %s", response.text()).isEqualTo(200);
    return response.json();
  }

  // ---- registration payloads -------------------------------------------------------------

  public static String uniqueEmail() {
    return "p-" + UUID.randomUUID().toString().substring(0, 12) + "@example.si";
  }

  public static ObjectNode external(String email) {
    ObjectNode body = JSON.createObjectNode();
    body.put("type", "EXTERNAL");
    body.put("firstName", "Ana");
    body.put("lastName", "Novak");
    body.put("email", email);
    body.put("organization", "Institut Jožef Stefan");
    body.set("optionIds", array("ws-testing-ai", "meal-lunch-day1"));
    body.set("consentIds", array("data-processing"));
    body.put("recaptchaToken", TEST_CAPTCHA_TOKEN);
    return body;
  }

  public static ObjectNode student(String email) {
    ObjectNode body = JSON.createObjectNode();
    body.put("type", "STUDENT");
    body.put("firstName", "Luka");
    body.put("lastName", "Kranjc");
    body.put("email", email);
    body.put("studyInstitution", "Univerza v Mariboru");
    body.put("studyProgramme", "Informatika");
    body.put("studentId", "E1234567");
    body.set("optionIds", array("ws-testing-ai"));
    body.set("consentIds", array("data-processing"));
    body.put("recaptchaToken", TEST_CAPTCHA_TOKEN);
    return body;
  }

  public static ArrayNode array(String... values) {
    ArrayNode array = JSON.createArrayNode();
    for (String value : values) {
      array.add(value);
    }
    return array;
  }

  public static Response register(Backend backend, Object body) {
    return postJson(backend.url("/api/registrations"), body);
  }

  // ---- assertions on problems ------------------------------------------------------------

  /** Asserts a problem response that names {@code field} with {@code code}. */
  public static void assertFieldError(Response response, int status, String field, String code) {
    assertThat(response.status()).as("status, body %s", response.text()).isEqualTo(status);
    assertThat(response.contentType()).startsWith("application/problem+json");
    JsonNode errors = response.json().get("errors");
    assertThat(errors).as("errors in %s", response.text()).isNotNull();
    List<String> pairs = new ArrayList<>();
    errors.forEach(e -> pairs.add(e.get("field").asString() + ":" + e.get("code").asString()));
    assertThat(pairs).contains(field + ":" + code);
  }

  /**
   * Card 3 step 1: a rejection leaves nothing behind. No export row, no new JSON copy, no email.
   */
  public static void assertNothingStoredFor(Backend backend, String email, int jsonCopiesBefore) {
    assertThat(exportRowsFor(backend, email)).as("export rows for %s", email).isEmpty();
    assertThat(jsonCopies(backend)).as("JSON copies").hasSize(jsonCopiesBefore);
    assertThat(mailsAfter(email, 1500)).as("emails mentioning %s", email).isEmpty();
  }

  // ---- export ----------------------------------------------------------------------------

  public static Response export(Backend backend) {
    return getWithBasic(
        backend.url("/api/registrations/export"),
        Backend.ORGANIZER_USERNAME,
        Backend.ORGANIZER_PASSWORD);
  }

  /** Rows of the first sheet as formatted strings, the heading row first. */
  public static List<List<String>> workbookRows(Response response) {
    assertThat(response.status()).as("export status").isEqualTo(200);
    assertThat(response.contentType()).startsWith(EXCEL);
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.body()))) {
      Sheet sheet = workbook.getSheet("Registrations");
      assertThat(sheet).as("sheet Registrations").isNotNull();
      DataFormatter formatter = new DataFormatter();
      List<List<String>> rows = new ArrayList<>();
      for (Row row : sheet) {
        List<String> cells = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
          Cell cell = row.getCell(i);
          cells.add(cell == null ? "" : formatter.formatCellValue(cell));
        }
        rows.add(cells);
      }
      return rows;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Export rows (without headings) whose email column equals {@code email}. */
  public static List<List<String>> exportRowsFor(Backend backend, String email) {
    List<List<String>> rows = workbookRows(export(backend));
    int emailColumn = rows.get(0).indexOf("Email");
    assertThat(emailColumn).as("Email column").isNotNegative();
    return rows.stream()
        .skip(1)
        .filter(r -> r.size() > emailColumn && email.equalsIgnoreCase(r.get(emailColumn)))
        .toList();
  }

  public static String cell(List<List<String>> rows, List<String> row, String heading) {
    int column = rows.get(0).indexOf(heading);
    assertThat(column).as("column %s in %s", heading, rows.get(0)).isNotNegative();
    return column < row.size() ? row.get(column) : "";
  }

  // ---- JSON copies -----------------------------------------------------------------------

  public static List<Path> jsonCopies(Backend backend) {
    try (Stream<Path> files = Files.list(backend.jsonCopyDir())) {
      return files.filter(p -> p.getFileName().toString().endsWith(".json")).toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static JsonNode readJson(Path file) {
    try {
      return JSON.readTree(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // ---- Mailpit ---------------------------------------------------------------------------

  /** Messages in Mailpit whose text mentions {@code term}, as Mailpit message summaries. */
  public static List<JsonNode> mailsMentioning(String term) {
    String query = URLEncoder.encode("\"" + term + "\"", StandardCharsets.UTF_8);
    JsonNode result = get(AcceptanceStack.mailpitApi() + "/api/v1/search?query=" + query).json();
    List<JsonNode> messages = new ArrayList<>();
    result.path("messages").forEach(messages::add);
    return messages;
  }

  /** Waits up to 15 s until at least {@code count} messages mention {@code term}. */
  public static List<JsonNode> awaitMails(String term, int count) {
    long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
    List<JsonNode> messages = mailsMentioning(term);
    while (messages.size() < count && System.nanoTime() < deadline) {
      pause(250);
      messages = mailsMentioning(term);
    }
    return messages;
  }

  /**
   * Waits {@code millis} and returns the messages mentioning {@code term}, for "no email" checks.
   */
  public static List<JsonNode> mailsAfter(String term, long millis) {
    pause(millis);
    return mailsMentioning(term);
  }

  public static JsonNode mailDetail(JsonNode summary) {
    return get(AcceptanceStack.mailpitApi() + "/api/v1/message/" + summary.get("ID").asString())
        .json();
  }

  public static byte[] mailPart(JsonNode detail, String partId) {
    return get(AcceptanceStack.mailpitApi()
            + "/api/v1/message/"
            + detail.get("ID").asString()
            + "/part/"
            + partId)
        .body();
  }

  public static List<String> recipients(JsonNode message) {
    List<String> to = new ArrayList<>();
    message.path("To").forEach(a -> to.add(a.get("Address").asString()));
    return to;
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
