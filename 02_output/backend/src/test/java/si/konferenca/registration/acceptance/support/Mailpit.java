package si.konferenca.registration.acceptance.support;

import static org.awaitility.Awaitility.await;

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

/** Reads the emails caught by Mailpit through its HTTP API. */
public final class Mailpit {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private Mailpit() {}

  /** Summaries of all caught messages, newest first. */
  public static List<JsonNode> messages() {
    JsonNode page = Api.JSON.readTree(get("/api/v1/messages?limit=500"));
    List<JsonNode> result = new ArrayList<>();
    page.path("messages").forEach(result::add);
    return result;
  }

  /** Full message: To, Subject, Text, Attachments (PartID, FileName, ContentType). */
  public static JsonNode message(String id) {
    return Api.JSON.readTree(get("/api/v1/message/" + id));
  }

  public static byte[] attachment(String messageId, String partId) {
    return get("/api/v1/message/" + messageId + "/part/" + partId);
  }

  /** Full messages addressed to the given address (in To). */
  public static List<JsonNode> messagesTo(String address) {
    List<JsonNode> result = new ArrayList<>();
    for (JsonNode summary : messages()) {
      for (JsonNode to : summary.path("To")) {
        if (to.path("Address").asString().equalsIgnoreCase(address)) {
          result.add(message(summary.path("ID").asString()));
          break;
        }
      }
    }
    return result;
  }

  /** Waits until at least the given number of messages has arrived. */
  public static List<JsonNode> awaitMessages(int count) {
    await()
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(250))
        .until(() -> messages().size() >= count);
    return messages();
  }

  /** Waits long enough for asynchronous sending, then returns what arrived. */
  public static List<JsonNode> messagesAfterQuietPeriod() {
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    return messages();
  }

  public static void deleteAll() {
    send(
        HttpRequest.newBuilder(uri("/api/v1/messages"))
            .method("DELETE", HttpRequest.BodyPublishers.noBody())
            .build());
  }

  private static URI uri(String path) {
    return URI.create(TestEnvironment.mailpitUrl() + path);
  }

  private static byte[] get(String path) {
    return send(HttpRequest.newBuilder(uri(path)).GET().build());
  }

  private static byte[] send(HttpRequest request) {
    try {
      HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() >= 300) {
        throw new IllegalStateException(
            "Mailpit " + request.uri().getPath() + " answered " + response.statusCode());
      }
      return response.body();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
