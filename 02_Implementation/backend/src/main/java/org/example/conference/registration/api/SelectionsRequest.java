package org.example.conference.registration.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Selected option IDs per group; absent lists mean no selection (normalized to empty). */
public record SelectionsRequest(
    List<@NotBlank @Size(max = 64) String> workshops,
    List<@NotBlank @Size(max = 64) String> events,
    List<@NotBlank @Size(max = 64) String> meals,
    List<@NotBlank @Size(max = 64) String> otherActivities) {

  public static final SelectionsRequest EMPTY = new SelectionsRequest(null, null, null, null);

  public SelectionsRequest {
    workshops = workshops == null ? List.of() : List.copyOf(workshops);
    events = events == null ? List.of() : List.copyOf(events);
    meals = meals == null ? List.of() : List.copyOf(meals);
    otherActivities = otherActivities == null ? List.of() : List.copyOf(otherActivities);
  }
}
