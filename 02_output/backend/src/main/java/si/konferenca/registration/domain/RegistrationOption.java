package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** Snapshot of a selected option at registration time. */
@Embeddable
public class RegistrationOption {

  @Column(name = "option_id", nullable = false, length = 64)
  private String optionId;

  @Column(name = "option_name", nullable = false, length = 200)
  private String optionName;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 16)
  private Category category;

  protected RegistrationOption() {}

  public RegistrationOption(ConferenceOption option) {
    this.optionId = option.id();
    this.optionName = option.name();
    this.category = option.category();
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
