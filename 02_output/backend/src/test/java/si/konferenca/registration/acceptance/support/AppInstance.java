package si.konferenca.registration.acceptance.support;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;

/**
 * Starts a separate instance of the application with its own settings, as a restart with a new
 * configuration would (US-003).
 */
public final class AppInstance implements AutoCloseable {

  private final ConfigurableApplicationContext context;

  private AppInstance(ConfigurableApplicationContext context) {
    this.context = context;
  }

  /** Starts an instance with {@link TestStack} settings plus {@code overrides}; may throw. */
  public static AppInstance start(Map<String, Object> overrides) {
    Map<String, Object> properties = new LinkedHashMap<>(TestStack.properties(overrides));
    properties.put("server.port", "0");
    if (!overrides.containsKey("JSON_COPY_DIR")) {
      properties.put(
          "JSON_COPY_DIR", TestStack.newTempDirectory("registration-copies-instance").toString());
    }
    return new AppInstance(
        new SpringApplicationBuilder(RegistrationApplication.class).properties(properties).run());
  }

  public ApiClient api() {
    return new ApiClient(
        Integer.parseInt(context.getEnvironment().getProperty("local.server.port")));
  }

  @Override
  public void close() {
    context.close();
  }
}
