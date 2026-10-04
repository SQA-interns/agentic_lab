package si.konferenca.registration.acceptance.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Reads the mail catcher through its HTTP API and switches its failure injection. */
public final class Mailpit {

  private final HttpClient http = HttpClient.newHttpClient();
  private final String base;

  public Mailpit(String base) {
    this.base = base;
  }

  /** Full message details (From, To, Subject, Text, Attachments) of every message to an address. */
  public List<JsonNode> messagesTo(String address) {
    String query = URLEncoder.encode("to:\"" + address + "\"", UTF_8);
    JsonNode list = ApiClient.JSON.readTree(get("/api/v1/search?limit=200&query=" + query));
    List<JsonNode> messages = new ArrayList<>();
    for (JsonNode summary : list.get("messages")) {
      messages.add(message(summary.get("ID").asString()));
    }
    return messages;
  }

  /** Waits until at least {@code count} messages to the address exist, then returns them all. */
  public List<JsonNode> awaitMessagesTo(String address, int count, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    List<JsonNode> messages = messagesTo(address);
    while (messages.size() < count && System.nanoTime() < deadline) {
      pause(200);
      messages = messagesTo(address);
    }
    return messages;
  }

  /** Waits until a message to the address contains the marker, then returns all such messages. */
  public List<JsonNode> awaitMessagesTo(String address, String textMarker, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    List<JsonNode> messages = messagesTo(address, textMarker);
    while (messages.isEmpty() && System.nanoTime() < deadline) {
      pause(200);
      messages = messagesTo(address, textMarker);
    }
    return messages;
  }

  /** Messages to an address whose text contains the given marker. */
  public List<JsonNode> messagesTo(String address, String textMarker) {
    return messagesTo(address).stream()
        .filter(m -> m.get("Text").asString().contains(textMarker))
        .toList();
  }

  public JsonNode message(String id) {
    return ApiClient.JSON.readTree(get("/api/v1/message/" + id));
  }

  public byte[] attachment(String messageId, String partId) {
    return getBytes("/api/v1/message/" + messageId + "/part/" + partId);
  }

  /** Makes the mail catcher refuse every recipient (true) or accept mail again (false). */
  public void failAllDeliveries(boolean fail) {
    String body =
        "{\"Sender\":{\"ErrorCode\":451,\"Probability\":0},"
            + "\"Recipient\":{\"ErrorCode\":451,\"Probability\":"
            + (fail ? 100 : 0)
            + "},\"Authentication\":{\"ErrorCode\":535,\"Probability\":0}}";
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(base + "/api/v1/chaos"))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build();
    send(request, HttpResponse.BodyHandlers.ofString());
  }

  /** Waits briefly so that a message that should not be sent would have arrived by now. */
  public static void settle() {
    pause(1000);
  }

  private String get(String path) {
    return new String(getBytes(path), UTF_8);
  }

  private byte[] getBytes(String path) {
    HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).GET().build();
    HttpResponse<byte[]> response = send(request, HttpResponse.BodyHandlers.ofByteArray());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("mailpit " + path + " -> " + response.statusCode());
    }
    return response.body();
  }

  private <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    try {
      return http.send(request, handler);
    } catch (IOException e) {
      throw new IllegalStateException("mailpit request failed", e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted", e);
    }
  }

  private static void pause(long millis) {
    try {
      Thread.sleep(Duration.ofMillis(millis));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
