package org.example.conference.registration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Selected option IDs per group; null lists mean no selection. */
public record SelectionsRequest(
    List<@NotBlank @Size(max = 64) String> workshops,
    List<@NotBlank @Size(max = 64) String> events,
    List<@NotBlank @Size(max = 64) String> meals,
    List<@NotBlank @Size(max = 64) String> otherActivities) {

  public static final SelectionsRequest EMPTY = new SelectionsRequest(null, null, null, null);
}
