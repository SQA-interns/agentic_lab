package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** An option as it was when the registration was accepted (registration_option). */
@Embeddable
public class SelectedOption {

  @Column(name = "option_id", nullable = false, length = 64)
  private String optionId;

  @Column(name = "option_name", nullable = false, length = 200)
  private String optionName;

  @Column(name = "category", nullable = false, length = 16)
  private Category category;

  protected SelectedOption() {}

  public SelectedOption(String optionId, String optionName, Category category) {
    this.optionId = optionId;
    this.optionName = optionName;
    this.category = category;
  }

  public String optionId() {
    return optionId;
  }

  public String optionName() {
    return optionName;
  }

  public Category category() {
    return category;
  }
}
