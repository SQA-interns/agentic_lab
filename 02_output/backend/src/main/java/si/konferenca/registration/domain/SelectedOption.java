package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * An option chosen in a registration, with the name and category it had when the registration was
 * accepted (AC-003-04).
 */
@Embeddable
public class SelectedOption {

  @Column(name = "option_id", nullable = false, length = 64)
  private String optionId;

  @Column(name = "option_name", nullable = false, length = 200)
  private String optionName;

  @Column(name = "option_category", nullable = false, length = 16)
  private String optionCategory;

  protected SelectedOption() {}

  public SelectedOption(String optionId, String optionName, OptionCategory category) {
    this.optionId = optionId;
    this.optionName = optionName;
    this.optionCategory = category.value();
  }

  public String optionId() {
    return optionId;
  }

  public String optionName() {
    return optionName;
  }

  public OptionCategory category() {
    return OptionCategory.fromValue(optionCategory).orElseThrow();
  }
}
