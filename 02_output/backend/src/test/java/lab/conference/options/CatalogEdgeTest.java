package lab.conference.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CatalogEdgeTest {

  private static Catalog parse(String json) throws Exception {
    return new CatalogLoader().parse(new ObjectMapper().readTree(json));
  }

  private static String catalog(String consent, String workshop) {
    return "{\"conferenceTitle\":\"x\""
        + consent
        + ",\"groups\":{\"workshops\":["
        + workshop
        + "],\"events\":[],\"meals\":[],\"other\":[]}}";
  }

  @Test
  void consentWithUnknownFieldIsRejected() {
    assertThatThrownBy(
            () ->
                parse(
                    catalog(
                        ",\"consent\":{\"id\":\"c\",\"text\":\"t\",\"required\":true,\"x\":1}",
                        "")))
        .hasMessageContaining("unknown field in consent");
  }

  @Test
  void nonObjectConsentAndOptionAreRejectedWithTheirOwnMessage() {
    assertThatThrownBy(() -> parse(catalog(",\"consent\":[]", "")))
        .hasMessageContaining("consent must be a JSON object");
    assertThatThrownBy(() -> parse(catalog("", "[]")))
        .hasMessageContaining("option in workshops must be a JSON object");
    assertThatThrownBy(() -> parse("[]")).hasMessageContaining("catalog must be a JSON object");
  }

  @Test
  void nameOfExactlyMaximumLengthIsAccepted() throws Exception {
    String name = "n".repeat(200);
    Catalog c = parse(catalog("", "{\"id\":\"a\",\"name\":\"" + name + "\",\"active\":true}"));
    assertThat(c.activeOptions(GroupId.WORKSHOPS).get(0).name()).hasSize(200);
  }

  @Test
  void unknownGroupMessageNamesTheGroup() {
    assertThatThrownBy(() -> parse("{\"conferenceTitle\":\"x\",\"groups\":{\"tours\":[]}}"))
        .hasMessage("unknown option group: tours");
  }
}
