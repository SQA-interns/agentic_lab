package si.konferenca.registration.acceptance.support;

import org.springframework.context.ConfigurableApplicationContext;

/** A started backend instance, reachable only through its HTTP base URL. */
public final class RunningApp implements AutoCloseable {

  private final ConfigurableApplicationContext context;
  private final Api api;

  RunningApp(ConfigurableApplicationContext context, String baseUrl) {
    this.context = context;
    this.api = new Api(baseUrl);
  }

  public Api api() {
    return api;
  }

  @Override
  public void close() {
    context.close();
  }
}
