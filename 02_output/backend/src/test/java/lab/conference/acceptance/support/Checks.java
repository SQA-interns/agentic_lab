package lab.conference.acceptance.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

/** Shared assertions for rejection criteria ("nothing stored"). */
public final class Checks {

  private Checks() {}

  /**
   * Asserts the rejection left no durable trace: no DB row, JSON file or notification intent, and
   * no email to the participant address after several notification poll intervals.
   */
  public static void assertNothingStored(
      AppInstance app, Store.Snapshot before, String participantEmail) {
    Mailpit.sleep(Duration.ofSeconds(2).toMillis());
    app.store().assertUnchangedSince(before);
    if (participantEmail != null) {
      assertThat(app.store().registrationsWithEmail(participantEmail)).isZero();
      assertThat(Mailpit.shared().messagesTo(participantEmail)).isEmpty();
    }
  }
}
