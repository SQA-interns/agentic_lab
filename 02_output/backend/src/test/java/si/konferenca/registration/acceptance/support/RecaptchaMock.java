package si.konferenca.registration.acceptance.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local stand-in for the Google siteverify endpoint (02_contracts/recaptcha.md). Accepts the token
 * {@link #GOOD_TOKEN}, answers HTTP 500 for {@link #SERVER_ERROR_TOKEN} and success=false for all
 * others. Records every form body it receives.
 */
public final class RecaptchaMock {

  public static final String SECRET = "test-only-recaptcha-secret";
  public static final String SITE_KEY = "test-only-recaptcha-site-key";
  public static final String GOOD_TOKEN = "google-accepts-this-token";
  public static final String SERVER_ERROR_TOKEN = "google-fails-with-500";

  private static RecaptchaMock instance;

  private final HttpServer server;
  private final List<Map<String, String>> requests = new CopyOnWriteArrayList<>();

  private RecaptchaMock() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext(
        "/siteverify",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          Map<String, String> form = parse(body);
          form.put("_method", exchange.getRequestMethod());
          form.put("_contentType", exchange.getRequestHeaders().getFirst("Content-Type"));
          requests.add(form);
          String token = form.getOrDefault("response", "");
          int status = SERVER_ERROR_TOKEN.equals(token) ? 500 : 200;
          boolean ok = GOOD_TOKEN.equals(token) && SECRET.equals(form.get("secret"));
          String json =
              ok
                  ? "{\"success\":true,\"challenge_ts\":\"2026-09-30T10:00:00Z\","
                      + "\"hostname\":\"localhost\"}"
                  : "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";
          byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
          }
        });
    server.start();
  }

  public static synchronized RecaptchaMock get() {
    if (instance == null) {
      instance = new RecaptchaMock();
    }
    return instance;
  }

  public String url() {
    return "http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify";
  }

  public List<Map<String, String>> requests() {
    return requests;
  }

  private static Map<String, String> parse(String body) {
    Map<String, String> m = new LinkedHashMap<>();
    for (String pair : body.split("&")) {
      int i = pair.indexOf('=');
      if (i > 0) {
        m.put(
            URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
            URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
      }
    }
    return m;
  }
}
