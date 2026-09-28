package org.example.conference.notification;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Email outbox settings; organizer recipients come from environment. */
@Validated
@ConfigurationProperties(prefix = "app.mail")
public record NotificationProperties(
    @NotBlank String from,
    @NotEmpty List<@NotBlank String> organizerRecipients,
    boolean dispatchEnabled,
    @Min(1) int maxAttempts,
    @Min(1) int batchSize,
    Duration initialBackoff,
    Duration maxBackoff) {}
