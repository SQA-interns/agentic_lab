package lab.conference.acceptance.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/** Black-box HTTP client for the REST contract in docs/02_contracts/openapi.yaml. */
public final class Api {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  private final String baseUrl;

  public Api(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public Response getCatalog() {
    return send(HttpRequest.newBuilder(uri("/api/catalog")).GET());
  }

  public Response postExternal(Map<String, Object> body) {
    return postJson("/api/registrations/external", toJson(body));
  }

  public Response postStudent(Map<String, Object> body) {
    return postJson("/api/registrations/student", toJson(body));
  }

  public Response postJson(String path, String rawBody) {
    return send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(rawBody, StandardCharsets.UTF_8)));
  }

  public Response export(String user, String password) {
    HttpRequest.Builder b = HttpRequest.newBuilder(uri("/api/organizer/export.xlsx")).GET();
    if (user != null) {
      String token =
          Base64.getEncoder()
              .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
      b.header("Authorization", "Basic " + token);
    }
    return send(b);
  }

  public static String toJson(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private URI uri(String path) {
    return URI.create(baseUrl + path);
  }

  private static Response send(HttpRequest.Builder builder) {
    try {
      HttpResponse<byte[]> r =
          CLIENT.send(
              builder.timeout(Duration.ofSeconds(90)).build(),
              HttpResponse.BodyHandlers.ofByteArray());
      return new Response(r);
    } catch (IOException e) {
      throw new IllegalStateException("HTTP request failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** Captured HTTP response. */
  public static final class Response {
    private final HttpResponse<byte[]> raw;

    Response(HttpResponse<byte[]> raw) {
      this.raw = raw;
    }

    public int status() {
      return raw.statusCode();
    }

    public byte[] bytes() {
      return raw.body();
    }

    public String text() {
      return new String(raw.body(), StandardCharsets.UTF_8);
    }

    public String header(String name) {
      return raw.headers().firstValue(name).orElse(null);
    }

    public JsonNode json() {
      try {
        return JSON.readTree(raw.body());
      } catch (IOException e) {
        throw new AssertionError("response is not JSON (status " + status() + "): " + text(), e);
      }
    }

    /** True if the problem response lists an error for the field (optionally with the code). */
    public boolean hasFieldError(String field, String code) {
      if (status() != 400) {
        return false;
      }
      JsonNode errors = json().path("errors");
      for (JsonNode e : errors) {
        if (field.equals(e.path("field").asText())
            && (code == null || code.equals(e.path("code").asText()))) {
          return true;
        }
      }
      return false;
    }

    @Override
    public String toString() {
      return "HTTP " + status() + " " + text();
    }
  }
}
