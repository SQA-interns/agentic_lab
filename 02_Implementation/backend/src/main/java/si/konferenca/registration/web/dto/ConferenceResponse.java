package si.konferenca.registration.web.dto;

import java.util.List;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.ConsentDefinition;
import si.konferenca.registration.domain.OptionCategory;

/** Body of {@code GET /api/conference}: everything the frontend needs to render the forms. */
public record ConferenceResponse(
    List<OptionDto> options, List<ConsentDto> consents, RecaptchaDto recaptcha) {

  public record OptionDto(String id, String name, OptionCategory category) {
    public static OptionDto of(ConferenceOption option) {
      return new OptionDto(option.id(), option.name(), option.category());
    }
  }

  public record ConsentDto(String id, String label, boolean required) {
    public static ConsentDto of(ConsentDefinition consent) {
      return new ConsentDto(consent.id(), consent.label(), consent.required());
    }
  }

  public record RecaptchaDto(String siteKey, boolean testMode) {}
}
