package si.konferenca.registration.adapter.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A selected option as stored, with name and category at submission time. */
@Embeddable
public class OptionColumns {

  @Column(name = "option_id", nullable = false, length = 63)
  private String optionId;

  @Column(name = "option_name", nullable = false, length = 200)
  private String optionName;

  @Column(name = "category", nullable = false, length = 10)
  private String category;

  protected OptionColumns() {}

  OptionColumns(String optionId, String optionName, String category) {
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

  String category() {
    return category;
  }
}
