package si.konferenca.registration.domain;

/** An option as it was configured when the registration was accepted. */
public record SelectedOption(String id, String name, Category category) {}
