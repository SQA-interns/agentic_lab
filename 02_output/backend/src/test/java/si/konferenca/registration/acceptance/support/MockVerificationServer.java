package si.konferenca.registration.acceptance.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local stand-in for the reCAPTCHA siteverify endpoint (`recaptcha-siteverify.openapi.yaml`), so
 * the production verification path runs without calling Google (DoD-P05).
 */
public final class MockVerificationServer {

  public static final String VALID_TOKEN = "valid-live-token";

  /** How the mock answers. */
  public enum Mode {
    VERIFY,
    SERVER_ERROR
  }

  private static final List<Map<String, String>> REQUESTS = new CopyOnWriteArrayList<>();
  private static volatile Mode mode = Mode.VERIFY;
  private static final HttpServer SERVER = start();

  private MockVerificationServer() {}

  public static String url() {
    return "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/siteverify";
  }

  public static void reset() {
    REQUESTS.clear();
    mode = Mode.VERIFY;
  }

  public static void answerWith(Mode newMode) {
    mode = newMode;
  }

  /** Form fields of every request received, in order. */
  public static List<Map<String, String>> requests() {
    return List.copyOf(REQUESTS);
  }

  private static HttpServer start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/siteverify",
          exchange -> {
            Map<String, String> form =
                parseForm(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            REQUESTS.add(form);
            byte[] body;
            int status;
            if (mode == Mode.SERVER_ERROR) {
              status = 500;
              body = "{}".getBytes(StandardCharsets.UTF_8);
            } else {
              status = 200;
              boolean success = VALID_TOKEN.equals(form.get("response"));
              body =
                  (success
                          ? "{\"success\": true, \"hostname\": \"localhost\"}"
                          : "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}")
                      .getBytes(StandardCharsets.UTF_8);
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, String> parseForm(String body) {
    Map<String, String> form = new LinkedHashMap<>();
    for (String pair : body.split("&")) {
      if (pair.isEmpty()) {
        continue;
      }
      int eq = pair.indexOf('=');
      String key = eq < 0 ? pair : pair.substring(0, eq);
      String value = eq < 0 ? "" : pair.substring(eq + 1);
      form.put(
          URLDecoder.decode(key, StandardCharsets.UTF_8),
          URLDecoder.decode(value, StandardCharsets.UTF_8));
    }
    return form;
  }
}
