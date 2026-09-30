package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** An option chosen at registration time, with the name and category shown then. */
@Embeddable
public class SelectedOption {

  @Column(name = "option_id", nullable = false, length = 64)
  private String optionId;

  @Column(name = "option_name", nullable = false, length = 200)
  private String optionName;

  @Column(name = "category", nullable = false, length = 10)
  private String category;

  protected SelectedOption() {}

  public SelectedOption(String optionId, String optionName, String category) {
    this.optionId = optionId;
    this.optionName = optionName;
    this.category = category;
  }

  public String getOptionId() {
    return optionId;
  }

  public String getOptionName() {
    return optionName;
  }

  public String getCategory() {
    return category;
  }
}
