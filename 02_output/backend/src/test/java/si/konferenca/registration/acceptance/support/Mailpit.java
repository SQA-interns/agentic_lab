package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
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

/** Reads delivered messages from the Mailpit HTTP API and controls its chaos (SMTP errors). */
public final class Mailpit {

  private final String baseUrl;
  private final HttpClient client = HttpClient.newHttpClient();

  Mailpit(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  /** A delivered message. */
  public record Message(
      String id,
      List<String> to,
      List<String> cc,
      List<String> bcc,
      String subject,
      String text,
      String html,
      JsonNode attachments) {}

  /** All messages whose To contains the address. */
  public List<Message> messagesTo(String address) {
    JsonNode list =
        getJson("/api/v1/search?limit=200&query=" + encode("to:\"" + address + "\""))
            .path("messages");
    List<Message> result = new ArrayList<>();
    for (JsonNode summary : list) {
      Message m = message(summary.path("ID").asString());
      if (m.to().stream().anyMatch(a -> a.equalsIgnoreCase(address))) {
        result.add(m);
      }
    }
    return result;
  }

  /** Messages to the address whose text contains the marker (e.g. a registration reference). */
  public List<Message> messagesTo(String address, String marker) {
    return messagesTo(address).stream().filter(m -> m.text().contains(marker)).toList();
  }

  /** Waits until at least {@code count} messages to the address contain the marker. */
  public List<Message> awaitMessages(String address, String marker, int count) {
    return Awaitility.await()
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .until(() -> messagesTo(address, marker), l -> l.size() >= count);
  }

  public Message message(String id) {
    JsonNode m = getJson("/api/v1/message/" + id);
    return new Message(
        id,
        addresses(m.path("To")),
        addresses(m.path("Cc")),
        addresses(m.path("Bcc")),
        m.path("Subject").asString(),
        m.path("Text").asString(),
        m.path("HTML").asString(),
        m.path("Attachments"));
  }

  public JsonNode headers(String id) {
    return getJson("/api/v1/message/" + id + "/headers");
  }

  public byte[] part(String id, String partId) {
    return get("/api/v1/message/" + id + "/part/" + partId);
  }

  /** Makes every RCPT TO fail with a temporary SMTP error, or restores normal delivery. */
  public void failAllRecipients(boolean fail) {
    String body = "{\"Recipient\":{\"ErrorCode\":451,\"Probability\":" + (fail ? 100 : 0) + "}}";
    send(
        HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/chaos"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build());
  }

  private static List<String> addresses(JsonNode node) {
    List<String> result = new ArrayList<>();
    node.forEach(a -> result.add(a.path("Address").asString()));
    return result;
  }

  private JsonNode getJson(String path) {
    return Api.JSON.readTree(get(path));
  }

  private byte[] get(String path) {
    return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
  }

  private byte[] send(HttpRequest request) {
    try {
      HttpResponse<byte[]> r = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (r.statusCode() >= 300) {
        throw new IllegalStateException("Mailpit " + request.uri() + " -> " + r.statusCode());
      }
      return r.body();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static String encode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }
}
