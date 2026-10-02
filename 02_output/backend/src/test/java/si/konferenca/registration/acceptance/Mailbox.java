package si.konferenca.registration.acceptance;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** The mail catcher, read through its HTTP API. */
final class Mailbox {

  private Mailbox() {}

  /** An attachment of a caught message. */
  record Attachment(String partId, String fileName, String contentType) {}

  /** A caught message. */
  record Mail(
      String id,
      String from,
      List<String> to,
      String subject,
      String text,
      String html,
      List<Attachment> attachments) {

    byte[] attachmentBytes(Attachment attachment) {
      return Api.getUrl(
              Stack.mailApiUrl() + "/api/v1/message/" + id + "/part/" + attachment.partId())
          .body();
    }
  }

  static void clear() {
    Api.deleteUrl(Stack.mailApiUrl() + "/api/v1/messages");
  }

  static List<Mail> messages() {
    JsonNode list = Api.getUrl(Stack.mailApiUrl() + "/api/v1/messages?limit=200").json();
    List<Mail> mails = new ArrayList<>();
    for (JsonNode summary : list.path("messages")) {
      String id = summary.path("ID").asString();
      JsonNode message = Api.getUrl(Stack.mailApiUrl() + "/api/v1/message/" + id).json();
      List<String> to = new ArrayList<>();
      for (JsonNode recipient : message.path("To")) {
        to.add(recipient.path("Address").asString());
      }
      List<Attachment> attachments = new ArrayList<>();
      for (JsonNode attachment : message.path("Attachments")) {
        attachments.add(
            new Attachment(
                attachment.path("PartID").asString(),
                attachment.path("FileName").asString(),
                attachment.path("ContentType").asString()));
      }
      mails.add(
          new Mail(
              id,
              message.path("From").path("Address").asString(),
              to,
              message.path("Subject").asString(),
              message.path("Text").asString(),
              message.path("HTML").asString(),
              attachments));
    }
    return mails;
  }

  /** Waits until the given number of messages has arrived and returns all messages. */
  static List<Mail> awaitMessages(int count) {
    await()
        .atMost(Duration.ofSeconds(15))
        .pollInterval(Duration.ofMillis(200))
        .until(() -> messages().size() >= count);
    return messages();
  }

  /** The messages after a pause long enough for a wrongly sent message to arrive. */
  static List<Mail> messagesAfterSettling() {
    try {
      Thread.sleep(700);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
    return messages();
  }

  static List<Mail> sentTo(List<Mail> mails, String address) {
    return mails.stream().filter(mail -> mail.to().contains(address)).toList();
  }
}
