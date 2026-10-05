package si.konferenca.registration.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.application.RegistrationCommand;
import tools.jackson.databind.json.JsonMapper;

class RegistrationRequestReaderTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private static RegistrationCommand read(String json) {
    return RegistrationRequestReader.read(MAPPER.readTree(json));
  }

  @Test
  void readsEveryFieldAndKeepsNulls() {
    RegistrationCommand command =
        read(
            "{\"type\":\"STUDENT\",\"firstName\":\"Ž\",\"lastName\":null,\"email\":\"e\","
                + "\"studyInstitution\":\"UL\",\"studyProgramme\":\"FRI\",\"studentId\":\"1\","
                + "\"optionIds\":[\"a\",\"b\"],\"consentGiven\":true,\"captchaToken\":\"t\"}");

    assertThat(command.type()).isEqualTo("STUDENT");
    assertThat(command.firstName()).isEqualTo("Ž");
    assertThat(command.lastName()).isNull();
    assertThat(command.organization()).isNull();
    assertThat(command.studentId()).isEqualTo("1");
    assertThat(command.optionIds()).containsExactly("a", "b");
    assertThat(command.consentGiven()).isTrue();
    assertThat(command.captchaToken()).isEqualTo("t");
  }

  @Test
  void missingOrNullOptionsAndConsentAreReadAsAbsent() {
    RegistrationCommand command = read("{\"optionIds\":null,\"consentGiven\":null}");

    assertThat(command.optionIds()).isNull();
    assertThat(command.consentGiven()).isFalse();
    assertThat(read("{}").consentGiven()).isFalse();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "[]",
        "\"text\"",
        "{\"unknown\":1}",
        "{\"firstName\":1}",
        "{\"email\":{}}",
        "{\"optionIds\":\"ws-a\"}",
        "{\"optionIds\":[1]}",
        "{\"optionIds\":[null]}",
        "{\"consentGiven\":\"true\"}"
      })
  void malformedBodiesAreRejected(String json) {
    assertThatThrownBy(() -> read(json)).isInstanceOf(MalformedRequestException.class);
  }

  @Test
  void nullBodyIsMalformed() {
    assertThatThrownBy(() -> RegistrationRequestReader.read(null))
        .isInstanceOf(MalformedRequestException.class);
  }

  @Test
  void loopbackDetectionUsesLiteralAddressesOnly() {
    assertThat(OrganizerController.isLoopback("127.0.0.1")).isTrue();
    assertThat(OrganizerController.isLoopback("127.8.9.10")).isTrue();
    assertThat(OrganizerController.isLoopback("::1")).isTrue();
    assertThat(OrganizerController.isLoopback("0:0:0:0:0:0:0:1")).isTrue();
    assertThat(OrganizerController.isLoopback("10.0.0.1")).isFalse();
    assertThat(OrganizerController.isLoopback("172.18.0.5")).isFalse();
    assertThat(OrganizerController.isLoopback("")).isFalse();
    assertThat(OrganizerController.isLoopback(null)).isFalse();
  }
}
