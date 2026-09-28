package org.example.conference.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Selected option with a name snapshot taken at submission time. */
@Entity
@Table(name = "registration_selection")
public class RegistrationSelection {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "registration_id", nullable = false)
  private Registration registration;

  @Column(name = "option_group", nullable = false)
  private String optionGroup;

  @Column(nullable = false)
  private int position;

  @Column(name = "option_id", nullable = false)
  private String optionId;

  @Column(name = "option_name", nullable = false)
  private String optionName;

  protected RegistrationSelection() {}

  RegistrationSelection(
      Registration registration,
      String optionGroup,
      int position,
      String optionId,
      String optionName) {
    this.registration = registration;
    this.optionGroup = optionGroup;
    this.position = position;
    this.optionId = optionId;
    this.optionName = optionName;
  }

  public String getOptionGroup() {
    return optionGroup;
  }

  public String getOptionId() {
    return optionId;
  }

  public String getOptionName() {
    return optionName;
  }
}
