package lab.conference.options;

/** Configured consent statement; {@code required} makes it mandatory (BR-04). */
public record ConsentFixture(String id, String text, boolean required) {}
