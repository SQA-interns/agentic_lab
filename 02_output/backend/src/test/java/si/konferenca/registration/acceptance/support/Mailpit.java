package si.konferenca.registration.acceptance.support;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import tools.jackson.databind.JsonNode;

/** Client for the Mailpit HTTP API (the local SMTP substitute). */
public final class Mailpit {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private final String base;

  Mailpit(String base) {
    this.base = base;
  }

  /** Summaries of all messages sent to the address. */
  public List<JsonNode> messagesTo(String address) {
    JsonNode r =
        get(
            "/api/v1/search?query="
                + URLEncoder.encode("to:\"" + address + "\"", StandardCharsets.UTF_8));
    List<JsonNode> list = new ArrayList<>();
    r.path("messages").forEach(list::add);
    return list;
  }

  /** Waits until at least one message reached the address, then returns the full message. */
  public JsonNode awaitMessageTo(String address) {
    Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(250))
        .until(() -> !messagesTo(address).isEmpty());
    return message(messagesTo(address).get(0).path("ID").asString());
  }

  /** Asserts by waiting that no message reaches the address within the given time. */
  public boolean noMessageWithin(String address, Duration wait) {
    try {
      Thread.sleep(wait.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return messagesTo(address).isEmpty();
  }

  public JsonNode message(String id) {
    return get("/api/v1/message/" + id);
  }

  public byte[] attachment(String messageId, String partId) {
    return getBytes("/api/v1/message/" + messageId + "/part/" + partId);
  }

  public JsonNode headers(String messageId) {
    return get("/api/v1/message/" + messageId + "/headers");
  }

  private JsonNode get(String path) {
    return RunningApp.JSON.readTree(getBytes(path));
  }

  private byte[] getBytes(String path) {
    try {
      HttpResponse<byte[]> r =
          HTTP.send(
              HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
              HttpResponse.BodyHandlers.ofByteArray());
      if (r.statusCode() != 200) {
        throw new IllegalStateException("Mailpit " + path + " -> " + r.statusCode());
      }
      return r.body();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
