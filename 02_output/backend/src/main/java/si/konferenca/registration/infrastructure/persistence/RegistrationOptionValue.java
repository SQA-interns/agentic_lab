package si.konferenca.registration.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A selected option, copied at acceptance (registration_option). */
@Embeddable
public class RegistrationOptionValue {

  @Column(name = "option_id", nullable = false, length = 64)
  private String optionId;

  @Column(name = "option_name", nullable = false, length = 200)
  private String optionName;

  @Column(name = "option_category", nullable = false, length = 16)
  private String optionCategory;

  protected RegistrationOptionValue() {}

  public RegistrationOptionValue(String optionId, String optionName, String optionCategory) {
    this.optionId = optionId;
    this.optionName = optionName;
    this.optionCategory = optionCategory;
  }

  public String optionId() {
    return optionId;
  }

  public String optionName() {
    return optionName;
  }

  public String optionCategory() {
    return optionCategory;
  }
}
