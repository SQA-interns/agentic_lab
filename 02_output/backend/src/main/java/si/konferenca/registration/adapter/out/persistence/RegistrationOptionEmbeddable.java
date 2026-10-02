package si.konferenca.registration.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** Row of the table {@code registration_option} (registration-schema.sql). */
@Embeddable
class RegistrationOptionEmbeddable {

  @Column(name = "option_id")
  String optionId;

  @Column(name = "option_name")
  String optionName;

  @Column(name = "option_category")
  String optionCategory;

  RegistrationOptionEmbeddable() {}

  RegistrationOptionEmbeddable(String optionId, String optionName, String optionCategory) {
    this.optionId = optionId;
    this.optionName = optionName;
    this.optionCategory = optionCategory;
  }
}
