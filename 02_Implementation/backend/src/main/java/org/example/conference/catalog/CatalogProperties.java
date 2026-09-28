package org.example.conference.catalog;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Location of the external catalog file (AR-07). */
@Validated
@ConfigurationProperties(prefix = "app.catalog")
public record CatalogProperties(@NotBlank String path) {}
