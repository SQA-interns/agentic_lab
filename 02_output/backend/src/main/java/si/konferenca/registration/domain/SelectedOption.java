package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

/** An option as selected, with the display name and category valid at registration time. */
@Embeddable
public class SelectedOption {

  @Column(name = "option_id", nullable = false)
  private String optionId;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Category category;

  protected SelectedOption() {}

  public SelectedOption(String optionId, String displayName, Category category) {
    this.optionId = optionId;
    this.displayName = displayName;
    this.category = category;
  }

  public static SelectedOption of(ConferenceOption option) {
    return new SelectedOption(option.id(), option.displayName(), option.category());
  }

  public String optionId() {
    return optionId;
  }

  public String displayName() {
    return displayName;
  }

  public Category category() {
    return category;
  }
}
