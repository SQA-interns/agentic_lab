package si.konferenca.registration.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import si.konferenca.registration.domain.Category;

/** Row of {@code registration_option}. */
@Embeddable
public class OptionEntry {

  @Column(name = "option_id", nullable = false, length = 64)
  private String optionId;

  @Column(name = "option_name", nullable = false, length = 200)
  private String optionName;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 16)
  private Category category;

  protected OptionEntry() {}

  OptionEntry(String optionId, String optionName, Category category) {
    this.optionId = optionId;
    this.optionName = optionName;
    this.category = category;
  }

  String optionId() {
    return optionId;
  }

  String optionName() {
    return optionName;
  }

  Category category() {
    return category;
  }
}
