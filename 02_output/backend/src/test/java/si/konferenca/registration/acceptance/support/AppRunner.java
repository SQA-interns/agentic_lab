package si.konferenca.registration.acceptance.support;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;

/** Starts and stops a separate backend instance, for criteria about restarts. */
public final class AppRunner implements AutoCloseable {

  private final ConfigurableApplicationContext context;

  private AppRunner(ConfigurableApplicationContext context) {
    this.context = context;
  }

  /** Starts the application with the settings as command-line properties on a random port. */
  public static AppRunner start(Map<String, String> settings) {
    List<String> args = new ArrayList<>();
    settings.forEach((key, value) -> args.add("--" + key + "=" + value));
    args.add("--server.port=0");
    return new AppRunner(
        new SpringApplicationBuilder(RegistrationApplication.class)
            .run(args.toArray(String[]::new)));
  }

  public Api api() {
    String port = context.getEnvironment().getProperty("local.server.port");
    if (port == null) {
      throw new IllegalStateException("application did not publish local.server.port");
    }
    return new Api(URI.create("http://127.0.0.1:" + port));
  }

  @Override
  public void close() {
    context.close();
  }
}
