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
import tools.jackson.databind.JsonNode;

/** Reads delivered emails through the Mailpit API (the local SMTP substitute). */
public final class Mailpit {

  private static final HttpClient HTTP = HttpClient.newHttpClient();

  private Mailpit() {}

  /** A delivered message with its summary, full view and raw headers. */
  public record Message(JsonNode summary, JsonNode full, JsonNode headers) {

    public String id() {
      return summary.get("ID").asString();
    }

    public String subject() {
      return full.get("Subject").asString();
    }

    public String text() {
      return full.get("Text").asString();
    }

    public String html() {
      JsonNode h = full.get("HTML");
      return h == null || h.isNull() ? "" : h.asString();
    }

    public List<String> to() {
      List<String> r = new ArrayList<>();
      full.get("To").forEach(a -> r.add(a.get("Address").asString()));
      return r;
    }

    public String header(String name) {
      JsonNode values = headers.get(name);
      return values == null || values.isEmpty() ? null : values.get(0).asString();
    }

    public List<JsonNode> attachments() {
      List<JsonNode> r = new ArrayList<>();
      full.get("Attachments").forEach(r::add);
      return r;
    }

    public byte[] part(String partId) {
      return getBytes("/api/v1/message/" + id() + "/part/" + partId);
    }
  }

  /** Messages whose recipients include the address, waiting up to the given time. */
  public static List<Message> messagesTo(String address, int expected, Duration wait) {
    long end = System.nanoTime() + wait.toNanos();
    List<Message> found;
    do {
      found = search("to:\"" + address + "\"");
      if (found.size() >= expected) {
        return found;
      }
      pause();
    } while (System.nanoTime() < end);
    return found;
  }

  public static List<Message> search(String query) {
    JsonNode result =
        Api.JSON.readTree(
            getBytes(
                "/api/v1/search?limit=200&query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)));
    List<Message> r = new ArrayList<>();
    for (JsonNode s : result.get("messages")) {
      String id = s.get("ID").asString();
      JsonNode full = Api.JSON.readTree(getBytes("/api/v1/message/" + id));
      JsonNode headers = Api.JSON.readTree(getBytes("/api/v1/message/" + id + "/headers"));
      r.add(new Message(s, full, headers));
    }
    return r;
  }

  private static byte[] getBytes(String path) {
    try {
      HttpResponse<byte[]> r =
          HTTP.send(
              HttpRequest.newBuilder(URI.create(TestEnvironment.mailpitApiUrl() + path)).build(),
              HttpResponse.BodyHandlers.ofByteArray());
      if (r.statusCode() != 200) {
        throw new IllegalStateException("Mailpit " + path + " returned " + r.statusCode());
      }
      return r.body();
    } catch (java.io.IOException e) {
      throw new IllegalStateException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void pause() {
    try {
      Thread.sleep(250);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
