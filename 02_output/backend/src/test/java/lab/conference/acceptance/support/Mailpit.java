package lab.conference.acceptance.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.testcontainers.containers.GenericContainer;

/** Client for the Mailpit HTTP API of the isolated SMTP catcher. */
public final class Mailpit {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private final String base;

  public Mailpit(GenericContainer<?> container) {
    this.base = "http://" + container.getHost() + ":" + container.getMappedPort(8025);
  }

  public static Mailpit shared() {
    return new Mailpit(Infra.mailpit());
  }

  /** Message summaries addressed to the given recipient. */
  public List<JsonNode> messagesTo(String address) {
    JsonNode result =
        getJson(
            "/api/v1/search?limit=200&query="
                + URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8));
    List<JsonNode> list = new ArrayList<>();
    result.path("messages").forEach(list::add);
    return list;
  }

  /** Waits until at least {@code count} messages to the address exist. */
  public List<JsonNode> awaitMessagesTo(String address, int count, Duration timeout) {
    Instant deadline = Instant.now().plus(timeout);
    List<JsonNode> found = messagesTo(address);
    while (found.size() < count && Instant.now().isBefore(deadline)) {
      sleep(300);
      found = messagesTo(address);
    }
    if (found.size() < count) {
      throw new AssertionError(
          "expected " + count + " message(s) to " + address + ", found " + found.size());
    }
    return found;
  }

  /** Full message (Text, HTML, To, Cc, Bcc, Subject, Attachments). */
  public JsonNode message(String id) {
    return getJson("/api/v1/message/" + id);
  }

  public JsonNode headers(String id) {
    return getJson("/api/v1/message/" + id + "/headers");
  }

  public byte[] part(String id, String partId) {
    return get("/api/v1/message/" + id + "/part/" + partId);
  }

  private JsonNode getJson(String path) {
    try {
      return JSON.readTree(get(path));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private byte[] get(String path) {
    try {
      HttpResponse<byte[]> r =
          CLIENT.send(
              HttpRequest.newBuilder(URI.create(base + path))
                  .timeout(Duration.ofSeconds(10))
                  .build(),
              HttpResponse.BodyHandlers.ofByteArray());
      if (r.statusCode() != 200) {
        throw new IllegalStateException("Mailpit " + path + " -> " + r.statusCode());
      }
      return r.body();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  public static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
