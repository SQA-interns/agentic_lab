package si.konferenca.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A configurable conference option. Never deleted, only deactivated (specification §3.1). */
@Entity
@Table(name = "conference_option")
public class ConferenceOption {

  @Id
  @Column(length = 64)
  private String id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private OptionCategory category;

  @Column(nullable = false, length = 200)
  private String name;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  protected ConferenceOption() {}

  public ConferenceOption(
      String id, OptionCategory category, String name, boolean active, int sortOrder) {
    this.id = id;
    this.category = category;
    this.name = name;
    this.active = active;
    this.sortOrder = sortOrder;
  }

  /** Applies the options-file values for this identifier. */
  public void update(OptionCategory category, String name, boolean active, int sortOrder) {
    this.category = category;
    this.name = name;
    this.active = active;
    this.sortOrder = sortOrder;
  }

  public void deactivate() {
    this.active = false;
  }

  public String getId() {
    return id;
  }

  public OptionCategory getCategory() {
    return category;
  }

  public String getName() {
    return name;
  }

  public boolean isActive() {
    return active;
  }

  public int getSortOrder() {
    return sortOrder;
  }
}
