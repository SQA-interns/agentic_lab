package si.konferenca.registration.acceptance.support;

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

/** Reads delivered emails through the Mailpit HTTP API. */
public final class Mailpit {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private final String baseUrl;

  Mailpit(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  /** Full messages whose text contains the given term (for example a unique email address). */
  public List<Message> search(String term) {
    JsonNode result =
        Json.read(
            getText(
                "/api/v1/search?query="
                    + URLEncoder.encode("\"" + term + "\"", StandardCharsets.UTF_8)));
    List<Message> messages = new ArrayList<>();
    for (JsonNode summary : result.path("messages")) {
      messages.add(message(summary.path("ID").asString()));
    }
    return messages;
  }

  /** Messages containing the term addressed to the given recipient. */
  public List<Message> searchTo(String recipient, String term) {
    return search(term).stream().filter(m -> m.to().contains(recipient)).toList();
  }

  public Message message(String id) {
    JsonNode m = Json.read(getText("/api/v1/message/" + id));
    List<String> to = new ArrayList<>();
    for (JsonNode address : m.path("To")) {
      to.add(address.path("Address").asString());
    }
    List<Attachment> attachments = new ArrayList<>();
    for (JsonNode a : m.path("Attachments")) {
      attachments.add(
          new Attachment(
              a.path("FileName").asString(),
              a.path("ContentType").asString(),
              getBytes("/api/v1/message/" + id + "/part/" + a.path("PartID").asString())));
    }
    return new Message(
        id,
        m.path("From").path("Address").asString(),
        to,
        m.path("Subject").asString(),
        m.path("Text").asString(),
        m.path("HTML").asString(),
        attachments);
  }

  private String getText(String path) {
    return new String(getBytes(path), StandardCharsets.UTF_8);
  }

  private byte[] getBytes(String path) {
    try {
      HttpResponse<byte[]> response =
          CLIENT.send(
              HttpRequest.newBuilder(URI.create(baseUrl + path))
                  .timeout(Duration.ofSeconds(10))
                  .build(),
              HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        throw new IllegalStateException("Mailpit " + path + " -> " + response.statusCode());
      }
      return response.body();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  /** A delivered email. */
  public record Message(
      String id,
      String from,
      List<String> to,
      String subject,
      String text,
      String html,
      List<Attachment> attachments) {}

  /** An email attachment. */
  public record Attachment(String fileName, String contentType, byte[] content) {}
}
