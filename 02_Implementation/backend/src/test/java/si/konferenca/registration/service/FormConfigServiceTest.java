package si.konferenca.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;

class FormConfigServiceTest {

  @Test
  void returnsOnlyActiveOptionsOrderedByCategoryKeepingConfigurationOrder() {
    ConferenceOptionCatalog catalog = mock(ConferenceOptionCatalog.class);
    CaptchaVerifier captcha = mock(CaptchaVerifier.class);
    when(catalog.findAll())
        .thenReturn(
            List.of(
                new ConferenceOption("other", "Tour", OptionCategory.OTHER, true),
                new ConferenceOption("meal", "Lunch", OptionCategory.MEAL, true),
                new ConferenceOption("ws-b", "B", OptionCategory.WORKSHOP, true),
                new ConferenceOption("ws-off", "Off", OptionCategory.WORKSHOP, false),
                new ConferenceOption("ws-a", "A", OptionCategory.WORKSHOP, true),
                new ConferenceOption("event", "Dinner", OptionCategory.EVENT, true)));
    CaptchaVerifier.CaptchaSettings settings =
        new CaptchaVerifier.CaptchaSettings(CaptchaVerifier.CaptchaSettings.Mode.RECAPTCHA, "site");
    when(captcha.settings()).thenReturn(settings);

    FormConfigService.FormConfig config = new FormConfigService(catalog, captcha).formConfig();

    assertThat(config.options())
        .extracting(ConferenceOption::id)
        .containsExactly("ws-b", "ws-a", "event", "meal", "other");
    assertThat(config.consents())
        .extracting(Consents.ConsentDefinition::id)
        .containsExactly("privacy");
    assertThat(config.consents().get(0).mandatory()).isTrue();
    assertThat(config.captcha()).isEqualTo(settings);
  }
}
