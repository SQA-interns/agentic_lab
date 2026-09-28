package org.example.conference.catalog;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Loads the catalog once at startup; changes require a restart (AR-07). */
@Configuration(proxyBeanMethods = false)
public class CatalogConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(CatalogConfiguration.class);

  @Bean
  public Catalog catalog(CatalogProperties properties) {
    Catalog catalog = CatalogLoader.load(Path.of(properties.path()));
    int active = 0;
    int total = 0;
    for (OptionGroup group : OptionGroup.values()) {
      active += catalog.activeOptions(group).size();
      total += catalog.options(group).size();
    }
    LOG.info(
        "Loaded catalog from {}: {} options ({} active), {} consents",
        properties.path(),
        total,
        active,
        catalog.consents().size());
    return catalog;
  }
}
