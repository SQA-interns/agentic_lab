package si.konferenca.registration.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/** Reads mail captured by the Mailpit container through its HTTP API (v1). */
final class Mailbox {

  /** A captured message with its plain-text body and attachments. */
  record Mail(
      String id,
      List<String> to,
      String subject,
      String text,
      String contentType,
      List<Attachment> attachments) {}

  record Attachment(String fileName, String contentType, byte[] content) {}

  private Mailbox() {}

  private static String api(String path) {
    return "http://"
        + AcceptanceHarness.MAILPIT.getHost()
        + ":"
        + AcceptanceHarness.MAILPIT.getMappedPort(8025)
        + path;
  }

  private static JsonNode getJson(String path) {
    return AcceptanceHarness.send(HttpRequest.newBuilder(URI.create(api(path))).GET().build())
        .json();
  }

  /** All messages addressed to {@code address}. */
  static List<Mail> to(String address) {
    List<Mail> out = new ArrayList<>();
    JsonNode list = getJson("/api/v1/messages?limit=500");
    for (JsonNode m : list.path("messages")) {
      boolean match = false;
      for (JsonNode t : m.path("To")) {
        if (address.equalsIgnoreCase(t.path("Address").asText())) {
          match = true;
        }
      }
      if (match) {
        out.add(load(m.path("ID").asText()));
      }
    }
    return out;
  }

  private static Mail load(String id) {
    JsonNode m = getJson("/api/v1/message/" + id);
    List<String> to = new ArrayList<>();
    for (JsonNode t : m.path("To")) {
      to.add(t.path("Address").asText());
    }
    List<Attachment> attachments = new ArrayList<>();
    for (JsonNode a : m.path("Attachments")) {
      byte[] bytes =
          AcceptanceHarness.send(
                  HttpRequest.newBuilder(
                          URI.create(
                              api("/api/v1/message/" + id + "/part/" + a.path("PartID").asText())))
                      .GET()
                      .build())
              .body();
      attachments.add(
          new Attachment(a.path("FileName").asText(), a.path("ContentType").asText(), bytes));
    }
    JsonNode headers = getJson("/api/v1/message/" + id + "/headers").path("Content-Type");
    String contentType = headers.isArray() && headers.size() > 0 ? headers.get(0).asText() : "";
    return new Mail(
        id, to, m.path("Subject").asText(), m.path("Text").asText(), contentType, attachments);
  }

  /** Waits up to 15 s for a message to {@code address} matching {@code filter}. */
  static Mail awaitMail(String address, Predicate<Mail> filter) {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
    while (Instant.now().isBefore(deadline)) {
      Optional<Mail> found = to(address).stream().filter(filter).findFirst();
      if (found.isPresent()) {
        return found.get();
      }
      sleep(250);
    }
    throw new AssertionError("No mail to " + address + " matching the expectation within 15 s");
  }

  /** Waits a grace period, then returns every message to {@code address} matching the filter. */
  static List<Mail> afterGrace(String address, Predicate<Mail> filter) {
    sleep(2000);
    return to(address).stream().filter(filter).toList();
  }

  static String utf8(byte[] b) {
    return new String(b, StandardCharsets.UTF_8);
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
