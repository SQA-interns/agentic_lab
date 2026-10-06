package si.konferenca.registration.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** Reads delivered emails through the Mailpit API (local SMTP substitute, `project/stack.md`). */
public final class Mailpit {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final Duration POLL = Duration.ofMillis(200);

  private final String base = AcceptanceEnvironment.mailpitApiUrl();

  public void clear() {
    send(HttpRequest.newBuilder(URI.create(base + "/api/v1/messages")).DELETE());
  }

  /** Message summaries (From, To, Cc, Bcc, Subject, Attachments count). */
  public List<JsonNode> messages() {
    JsonNode list = Json.read(get("/api/v1/messages?limit=500"));
    List<JsonNode> messages = new ArrayList<>();
    list.path("messages").forEach(messages::add);
    return messages;
  }

  /** Full message (Text, HTML, Attachments with PartID) by id. */
  public JsonNode message(String id) {
    return Json.read(get("/api/v1/message/" + id));
  }

  /** All headers of a message, as delivered. */
  public JsonNode headers(String id) {
    return Json.read(get("/api/v1/message/" + id + "/headers"));
  }

  /** Raw bytes of one MIME part (for example an attachment). */
  public byte[] part(String id, String partId) {
    return get("/api/v1/message/" + id + "/part/" + partId);
  }

  /** Waits until a message addressed (To) to the address arrives and returns its full form. */
  public JsonNode awaitMessageTo(String address, Duration timeout) {
    long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      for (JsonNode summary : messages()) {
        if (Json.strings(summary.path("To"), "Address").stream()
            .anyMatch(address::equalsIgnoreCase)) {
          return message(summary.path("ID").asString());
        }
      }
      sleep(POLL);
    }
    throw new AssertionError("no email to " + address + " within " + timeout);
  }

  /** Waits for the duration and returns every message that arrived. */
  public List<JsonNode> messagesAfter(Duration wait) {
    sleep(wait);
    return messages();
  }

  private byte[] get(String path) {
    return send(HttpRequest.newBuilder(URI.create(base + path)).GET());
  }

  private static byte[] send(HttpRequest.Builder request) {
    try {
      HttpResponse<byte[]> response =
          CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() >= 300) {
        throw new IllegalStateException("Mailpit API returned " + response.statusCode());
      }
      return response.body();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void sleep(Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
