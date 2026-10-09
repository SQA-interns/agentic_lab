package si.konferenca.registration.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a {@link Category} by its lower-case value. */
@Converter(autoApply = true)
public class CategoryConverter implements AttributeConverter<Category, String> {

  @Override
  public String convertToDatabaseColumn(Category category) {
    return category == null ? null : category.value();
  }

  @Override
  public Category convertToEntityAttribute(String value) {
    return value == null
        ? null
        : Category.fromValue(value)
            .orElseThrow(() -> new IllegalStateException("Unknown category in database"));
  }
}
