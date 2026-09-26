package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/** Snapshot of a conference option as it was when the registration was accepted. */
@Embeddable
public class SelectedOption {

  @Column(name = "option_id", nullable = false, length = 64)
  private String optionId;

  @Column(name = "option_name", nullable = false, length = 200)
  private String optionName;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 16)
  private OptionCategory category;

  protected SelectedOption() {}

  public SelectedOption(String optionId, String optionName, OptionCategory category) {
    this.optionId = optionId;
    this.optionName = optionName;
    this.category = category;
  }

  public static SelectedOption of(ConferenceOption option) {
    return new SelectedOption(option.id(), option.name(), option.category());
  }

  public String getOptionId() {
    return optionId;
  }

  public String getOptionName() {
    return optionName;
  }

  public OptionCategory getCategory() {
    return category;
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
