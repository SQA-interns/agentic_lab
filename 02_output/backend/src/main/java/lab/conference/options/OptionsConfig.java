package lab.conference.options;

import java.nio.file.Path;
import lab.conference.platform.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Loads the catalog once at startup from CONFERENCE_CONFIG_PATH. */
@Configuration
public class OptionsConfig {

  @Bean
  Catalog catalog(AppProperties props) {
    if (props.catalogPath() == null || props.catalogPath().isBlank()) {
      throw new InvalidCatalogException("CONFERENCE_CONFIG_PATH must be set");
    }
    return new CatalogLoader().load(Path.of(props.catalogPath()));
  }
}
