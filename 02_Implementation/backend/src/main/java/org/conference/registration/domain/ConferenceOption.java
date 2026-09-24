package org.conference.registration.domain;

import java.util.Set;

/** A configurable conference option as defined in the options configuration file (US-003). */
public record ConferenceOption(
    String id,
    OptionCategory category,
    String name,
    boolean active,
    Set<RegistrationType> audiences) {

  public ConferenceOption {
    audiences = Set.copyOf(audiences);
  }

  public boolean availableTo(RegistrationType type) {
    return active && audiences.contains(type);
  }
}
