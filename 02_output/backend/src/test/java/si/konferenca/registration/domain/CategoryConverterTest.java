package si.konferenca.registration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CategoryConverterTest {

  private final CategoryConverter converter = new CategoryConverter();

  @Test
  void storesAndReadsTheLowerCaseValue() {
    for (Category category : Category.values()) {
      String column = converter.convertToDatabaseColumn(category);
      assertThat(column).isEqualTo(category.value());
      assertThat(converter.convertToEntityAttribute(column)).isEqualTo(category);
    }
  }

  @Test
  void nullStaysNullAndUnknownValuesFail() {
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
    assertThatThrownBy(() -> converter.convertToEntityAttribute("talk"))
        .isInstanceOf(IllegalStateException.class);
  }
}
