package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/**
 * An option selected in a registration. Stores the stable option identifier plus a snapshot of
 * category and display name at submission time.
 */
@Embeddable
public class SelectedOption {

  @Column(name = "option_id", nullable = false, length = 64)
  private String optionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 16)
  private OptionCategory category;

  @Column(name = "option_name", nullable = false, length = 200)
  private String optionName;

  protected SelectedOption() {
    // for JPA
  }

  public SelectedOption(String optionId, OptionCategory category, String optionName) {
    this.optionId = optionId;
    this.category = category;
    this.optionName = optionName;
  }

  public static SelectedOption of(ConferenceOption option) {
    return new SelectedOption(option.id(), option.category(), option.name());
  }

  public String getOptionId() {
    return optionId;
  }

  public OptionCategory getCategory() {
    return category;
  }

  public String getOptionName() {
    return optionName;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof SelectedOption that)) {
      return false;
    }
    return Objects.equals(optionId, that.optionId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(optionId);
  }
}
