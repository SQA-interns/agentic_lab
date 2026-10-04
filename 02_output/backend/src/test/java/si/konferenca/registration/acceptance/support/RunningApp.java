package si.konferenca.registration.acceptance.support;

import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;

/** Starts and stops a separate instance of the application with its own configuration. */
public final class RunningApp implements AutoCloseable {

  private final ConfigurableApplicationContext context;
  private final ApiClient api;

  private RunningApp(ConfigurableApplicationContext context) {
    this.context = context;
    Integer port = context.getEnvironment().getProperty("local.server.port", Integer.class);
    if (port == null) {
      throw new IllegalStateException("application did not report a server port");
    }
    this.api = new ApiClient(port);
  }

  public static RunningApp start(Map<String, Object> properties) {
    return new RunningApp(
        new SpringApplicationBuilder(RegistrationApplication.class)
            .profiles("test")
            .properties(properties)
            .run("--server.port=0"));
  }

  public ApiClient api() {
    return api;
  }

  @Override
  public void close() {
    context.close();
  }
}
