package si.konferenca.registration.integration;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A local stand-in for the reCAPTCHA verification endpoint (recaptcha-verify.schema.json), so the
 * production code path runs without any call to Google (DoD-P05).
 */
public final class MockVerificationEndpoint implements AutoCloseable {

  private final HttpServer server;
  private final List<String> requestBodies = new CopyOnWriteArrayList<>();
  private final List<String> contentTypes = new CopyOnWriteArrayList<>();
  private volatile int status = 200;
  private volatile String body = "{\"success\":true}";

  public MockVerificationEndpoint() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext(
        "/siteverify",
        exchange -> {
          requestBodies.add(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          contentTypes.add(String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
          byte[] answer = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, answer.length);
          exchange.getResponseBody().write(answer);
          exchange.close();
        });
    server.start();
  }

  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify";
  }

  /** What the endpoint answers from now on. */
  public void answer(int newStatus, String newBody) {
    this.status = newStatus;
    this.body = newBody;
  }

  /** The form bodies received so far. */
  public List<String> requestBodies() {
    return List.copyOf(requestBodies);
  }

  public List<String> contentTypes() {
    return List.copyOf(contentTypes);
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
