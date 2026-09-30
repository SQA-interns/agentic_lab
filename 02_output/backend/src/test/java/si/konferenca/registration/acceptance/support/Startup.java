package si.konferenca.registration.acceptance.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import si.konferenca.registration.RegistrationApplication;

/**
 * Starts the backend as its runtime would, with settings given as command-line arguments (highest
 * precedence, like environment variables), to observe whether it refuses to start.
 */
public final class Startup {

  private Startup() {}

  /** Outcome of a start attempt: the running context or the failure. */
  public record Outcome(ConfigurableApplicationContext context, Throwable failure) {

    public boolean started() {
      return context != null;
    }

    public int port() {
      return Integer.parseInt(context.getEnvironment().getProperty("local.server.port"));
    }
  }

  /** Tries to start with the properties; a started context is returned open for inspection. */
  public static Outcome tryStart(Map<String, String> properties) {
    List<String> args = new ArrayList<>();
    args.add("--server.port=0");
    properties.forEach(
        (k, v) -> {
          if (v != null) {
            args.add("--" + k + "=" + v);
          }
        });
    try {
      ConfigurableApplicationContext ctx =
          new SpringApplicationBuilder(RegistrationApplication.class)
              .run(args.toArray(String[]::new));
      return new Outcome(ctx, null);
    } catch (RuntimeException e) {
      return new Outcome(null, e);
    }
  }

  /** Tries to start and closes the context again; true when startup succeeded. */
  public static boolean starts(Map<String, String> properties) {
    Outcome outcome = tryStart(properties);
    if (outcome.started()) {
      outcome.context().close();
      return true;
    }
    return false;
  }
}
