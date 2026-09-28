package org.example.conference.shared.web;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Request-size and public-submission rate limits (AC-X-03). */
@Validated
@ConfigurationProperties(prefix = "app.web")
public record WebLimitsProperties(@Min(1024) int maxBodyBytes, @Min(1) int rateLimitPerMinute) {}
