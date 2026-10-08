package si.konferenca.registration.acceptance.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mocked Google reCAPTCHA verification endpoint (recaptcha-verify.openapi.yaml, DoD-P05). A token
 * is valid only when it equals {@link #VALID_TOKEN} and the secret equals {@link #SECRET_KEY}.
 */
public final class RecaptchaMock {

  public static final String SITE_KEY = "acceptance-site-key";
  public static final String SECRET_KEY = "acceptance-secret-key";
  public static final String VALID_TOKEN = "valid-token";

  private final HttpServer server;
  private final List<Map<String, String>> requests = new CopyOnWriteArrayList<>();

  private RecaptchaMock(HttpServer server) {
    this.server = server;
  }

  static RecaptchaMock start() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    RecaptchaMock mock = new RecaptchaMock(server);
    server.createContext(
        "/recaptcha/api/siteverify",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          Map<String, String> form = parseForm(body);
          mock.requests.add(form);
          boolean ok =
              "POST".equals(exchange.getRequestMethod())
                  && SECRET_KEY.equals(form.get("secret"))
                  && VALID_TOKEN.equals(form.get("response"));
          byte[] response =
              (ok
                      ? "{\"success\":true,\"challenge_ts\":\"2026-10-08T21:00:00Z\",\"hostname\":\"localhost\"}"
                      : "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
          }
        });
    server.start();
    return mock;
  }

  public String verifyUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/recaptcha/api/siteverify";
  }

  /** Form bodies received so far. */
  public List<Map<String, String>> requests() {
    return List.copyOf(requests);
  }

  private static Map<String, String> parseForm(String body) {
    Map<String, String> form = new HashMap<>();
    for (String pair : body.split("&")) {
      int eq = pair.indexOf('=');
      if (eq > 0) {
        form.put(
            URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
            URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
      }
    }
    return form;
  }
}
