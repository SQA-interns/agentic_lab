package org.example.conference.registration.backup;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Persistent raw-JSON backup location (AR-03) and reconciliation timing. */
@Validated
@ConfigurationProperties(prefix = "app.backup")
public record BackupProperties(
    @NotBlank String directory, Duration reconcileGrace, Duration reconcileInterval) {}
