package si.konferenca.registration.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class ApiExceptionHandlerTest {

  @Test
  void unexpectedErrorDescriptionContainsNoMessages() { // spec §7.10
    Exception e =
        new DataIntegrityViolationException(
            "could not execute statement",
            new IllegalStateException(
                "Failing row contains (Ana, Novak, ana.novak@example.si, Institut Jožef Stefan)"));

    String description = ApiExceptionHandler.describeWithoutMessages(e);

    assertThat(description)
        .contains("org.springframework.dao.DataIntegrityViolationException")
        .contains("caused by java.lang.IllegalStateException")
        .doesNotContain("Novak")
        .doesNotContain("ana.novak@example.si")
        .doesNotContain("could not execute statement");
  }

  @Test
  void unexpectedErrorResponseHasNoDetails() {
    var response =
        new ApiExceptionHandler().handleUnexpected(new IllegalStateException("secret ana@x.si"));

    assertThat(response.getStatusCode().value()).isEqualTo(500);
    assertThat(response.getBody().error()).isEqualTo("INTERNAL_ERROR");
    assertThat(response.getBody().message()).doesNotContain("ana@x.si");
  }
}
