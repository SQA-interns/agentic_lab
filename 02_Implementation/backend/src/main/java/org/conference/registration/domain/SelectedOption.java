package org.conference.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;

/**
 * An option chosen in a registration. The display name is a snapshot, so later configuration
 * changes do not rewrite historical registrations.
 */
@Embeddable
public class SelectedOption {

  @Column(name = "option_id", nullable = false, length = 64)
  private String optionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, length = 16)
  private OptionCategory category;

  @Column(name = "display_name", nullable = false, length = 200)
  private String displayName;

  protected SelectedOption() {}

  public SelectedOption(String optionId, OptionCategory category, String displayName) {
    this.optionId = optionId;
    this.category = category;
    this.displayName = displayName;
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

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof SelectedOption that && Objects.equals(optionId, that.optionId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(optionId);
  }
}
