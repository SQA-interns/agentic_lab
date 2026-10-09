package si.konferenca.registration.acceptance.support;

import static org.awaitility.Awaitility.await;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Reads the messages caught by Mailpit through its HTTP API (/api/v1). */
public final class Mailpit {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Duration TIMEOUT = Duration.ofSeconds(15);

  private final String baseUrl;
  private final HttpClient http = HttpClient.newHttpClient();

  Mailpit(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  /** A caught message: summary fields of /api/v1/message/{id}. */
  public record Message(
      String id,
      String from,
      List<String> to,
      List<String> cc,
      List<String> bcc,
      String subject,
      String text,
      String html,
      List<Attachment> attachments) {}

  public record Attachment(String partId, String fileName, String contentType) {}

  public void clear() {
    send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/messages")).DELETE().build());
  }

  /** Every message whose To list contains exactly this address (letter case ignored). */
  public List<Message> messagesTo(String address) {
    List<Message> result = new ArrayList<>();
    for (Message message : all()) {
      if (message.to().stream().anyMatch(to -> to.equalsIgnoreCase(address))) {
        result.add(message);
      }
    }
    return result;
  }

  /** Waits until exactly {@code count} messages to the address exist and returns them. */
  public List<Message> awaitMessagesTo(String address, int count) {
    await().atMost(TIMEOUT).until(() -> messagesTo(address).size() >= count);
    return messagesTo(address);
  }

  public List<Message> all() {
    JsonNode list = getJson("/api/v1/messages?limit=500");
    List<Message> messages = new ArrayList<>();
    for (JsonNode summary : list.path("messages")) {
      messages.add(message(summary.path("ID").asString()));
    }
    return messages;
  }

  public Message message(String id) {
    JsonNode node = getJson("/api/v1/message/" + encode(id));
    List<Attachment> attachments = new ArrayList<>();
    for (JsonNode attachment : node.path("Attachments")) {
      attachments.add(
          new Attachment(
              attachment.path("PartID").asString(),
              attachment.path("FileName").asString(),
              attachment.path("ContentType").asString()));
    }
    return new Message(
        id,
        node.path("From").path("Address").asString(),
        addresses(node.path("To")),
        addresses(node.path("Cc")),
        addresses(node.path("Bcc")),
        node.path("Subject").asString(),
        node.path("Text").asString(),
        node.path("HTML").asString(),
        attachments);
  }

  /** Raw bytes of an attachment, exactly as sent. */
  public byte[] attachment(String messageId, String partId) {
    HttpResponse<byte[]> response =
        send(
            HttpRequest.newBuilder(
                    URI.create(
                        baseUrl + "/api/v1/message/" + encode(messageId) + "/part/" + partId))
                .GET()
                .build());
    return response.body();
  }

  /** Header values of the message, as /api/v1/message/{id}/headers returns them. */
  public JsonNode headers(String messageId) {
    return getJson("/api/v1/message/" + encode(messageId) + "/headers");
  }

  private static List<String> addresses(JsonNode list) {
    List<String> addresses = new ArrayList<>();
    for (JsonNode entry : list) {
      addresses.add(entry.path("Address").asString());
    }
    return addresses;
  }

  private JsonNode getJson(String path) {
    HttpResponse<byte[]> response =
        send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build());
    if (response.statusCode() != 200) {
      throw new IllegalStateException("Mailpit " + path + " returned " + response.statusCode());
    }
    return JSON.readTree(response.body());
  }

  private HttpResponse<byte[]> send(HttpRequest request) {
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
