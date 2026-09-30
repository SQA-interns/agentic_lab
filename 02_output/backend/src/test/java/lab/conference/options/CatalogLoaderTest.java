package lab.conference.options;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CatalogLoaderTest {

  private static final String VALID =
      """
      {"conferenceTitle":" Lab ","consent":{"id":"c1","text":"Agree","required":true},
       "groups":{"workshops":[{"id":"w1","name":"W1","active":true},{"id":"w2","name":"W2","active":false}],
                 "events":[],"meals":[{"id":"m1","name":"M1","active":true}],"other":[]}}
      """;

  private final CatalogLoader loader = new CatalogLoader();

  private Catalog parse(String json) throws Exception {
    return loader.parse(new ObjectMapper().readTree(json));
  }

  @Test
  void loadsValidCatalogFromFile(@TempDir Path dir) throws Exception {
    Path file = dir.resolve("c.json");
    Files.writeString(file, VALID);
    Catalog c = loader.load(file);
    assertThat(c.conferenceTitle()).isEqualTo("Lab");
    assertThat(c.consent()).contains(new ConsentFixture("c1", "Agree", true));
    assertThat(c.activeOptions(GroupId.WORKSHOPS))
        .extracting(CatalogOption::id)
        .containsExactly("w1");
    assertThat(c.activeOption(GroupId.WORKSHOPS, "w2")).isEmpty();
    assertThat(c.activeOption(GroupId.MEALS, "w1")).isEmpty();
    assertThat(c.activeOption(GroupId.MEALS, "m1")).isPresent();
  }

  @Test
  void consentIsOptional() throws Exception {
    Catalog c =
        parse(
            VALID.replace("\"consent\":{\"id\":\"c1\",\"text\":\"Agree\",\"required\":true},", ""));
    assertThat(c.consent()).isEmpty();
  }

  @Test
  void missingOrUnreadableFileFails(@TempDir Path dir) throws Exception {
    assertThatThrownBy(() -> loader.load(dir.resolve("none.json")))
        .isInstanceOf(InvalidCatalogException.class);
    assertThatThrownBy(() -> loader.load(null)).isInstanceOf(InvalidCatalogException.class);
    Path bad = dir.resolve("bad.json");
    Files.writeString(bad, "{not json");
    assertThatThrownBy(() -> loader.load(bad)).hasMessageContaining("not valid JSON");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "{\"conferenceTitle\":\"x\"}",
        "{\"conferenceTitle\":\"\",\"groups\":{}}",
        "{\"conferenceTitle\":\"x\",\"groups\":{\"workshops\":[],\"events\":[],\"meals\":[]}}",
        "{\"conferenceTitle\":\"x\",\"extra\":1,\"groups\":{\"workshops\":[],\"events\":[],\"meals\":[],\"other\":[]}}",
        "{\"conferenceTitle\":\"x\",\"groups\":{\"workshops\":{},\"events\":[],\"meals\":[],\"other\":[]}}",
        "{\"conferenceTitle\":\"x\",\"groups\":{\"workshops\":[{\"id\":\"UPPER\",\"name\":\"n\",\"active\":true}],\"events\":[],\"meals\":[],\"other\":[]}}",
        "{\"conferenceTitle\":\"x\",\"groups\":{\"workshops\":[{\"id\":\"a\",\"name\":\"n\",\"active\":\"yes\"}],\"events\":[],\"meals\":[],\"other\":[]}}",
        "{\"conferenceTitle\":\"x\",\"groups\":{\"workshops\":[{\"id\":\"a\",\"name\":\"n\",\"active\":true,\"x\":1}],\"events\":[],\"meals\":[],\"other\":[]}}",
        "{\"conferenceTitle\":\"x\",\"groups\":{\"workshops\":[\"a\"],\"events\":[],\"meals\":[],\"other\":[]}}",
        "{\"conferenceTitle\":\"x\",\"consent\":{\"id\":\"Bad Id\",\"text\":\"t\",\"required\":true},\"groups\":{\"workshops\":[],\"events\":[],\"meals\":[],\"other\":[]}}",
        "{\"conferenceTitle\":\"x\",\"consent\":\"yes\",\"groups\":{\"workshops\":[],\"events\":[],\"meals\":[],\"other\":[]}}"
      })
  void invalidCatalogsAreRejected(String json) {
    assertThatThrownBy(() -> parse(json)).isInstanceOf(InvalidCatalogException.class);
  }

  @Test
  void duplicateIdsAcrossGroupsAreRejected() {
    String json =
        "{\"conferenceTitle\":\"x\",\"groups\":{\"workshops\":[{\"id\":\"a\",\"name\":\"n\",\"active\":true}],"
            + "\"events\":[{\"id\":\"a\",\"name\":\"n\",\"active\":false}],\"meals\":[],\"other\":[]}}";
    assertThatThrownBy(() -> parse(json)).hasMessageContaining("duplicate option id: a");
  }

  @Test
  void overlongNameIsRejected() {
    String json =
        "{\"conferenceTitle\":\"x\",\"groups\":{\"workshops\":[{\"id\":\"a\",\"name\":\""
            + "n".repeat(201)
            + "\",\"active\":true}],\"events\":[],\"meals\":[],\"other\":[]}}";
    assertThatThrownBy(() -> parse(json)).hasMessageContaining("longer than 200");
  }

  @Test
  void groupKeysMapToEnum() {
    assertThat(GroupId.fromKey("meals")).contains(GroupId.MEALS);
    assertThat(GroupId.fromKey("tours")).isEmpty();
    assertThat(GroupId.OTHER.label()).isEqualTo("Other activities");
  }
}
