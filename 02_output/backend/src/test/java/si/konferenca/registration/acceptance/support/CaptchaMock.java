package si.konferenca.registration.acceptance.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Local stand-in for the reCAPTCHA verification endpoint
 * (docs/02_contracts/recaptcha-siteverify.openapi.yaml). Token {@link #ACCEPTED} verifies, {@link
 * #REJECTED} does not, {@link #OUTAGE} answers 500.
 */
public final class CaptchaMock {

  public static final String ACCEPTED = "token-accepted";
  public static final String REJECTED = "token-rejected";
  public static final String OUTAGE = "token-outage";
  public static final String SECRET = "secret-key-for-acceptance-tests";
  public static final String SITE_KEY = "site-key-for-acceptance-tests";
  public static final String PATH = "/recaptcha/api/siteverify";

  private static final List<Map<String, String>> REQUESTS = new CopyOnWriteArrayList<>();
  private static final HttpServer SERVER = start();

  private CaptchaMock() {}

  public static String verifyUrl() {
    return "http://localhost:" + SERVER.getAddress().getPort() + PATH;
  }

  /** Form fields of every verification request received, oldest first. */
  public static List<Map<String, String>> requests() {
    return List.copyOf(REQUESTS);
  }

  public static void clear() {
    REQUESTS.clear();
  }

  private static HttpServer start() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          PATH,
          exchange -> {
            String body =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> form = parseForm(body);
            REQUESTS.add(form);
            String token = form.getOrDefault("response", "");
            int status = OUTAGE.equals(token) ? 500 : 200;
            String answer =
                ACCEPTED.equals(token) && SECRET.equals(form.get("secret"))
                    ? "{\"success\": true, \"hostname\": \"localhost\"}"
                    : "{\"success\": false, \"error-codes\": [\"invalid-input-response\"]}";
            byte[] bytes = answer.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Map<String, String> parseForm(String body) {
    return java.util.Arrays.stream(body.split("&"))
        .filter(pair -> pair.contains("="))
        .collect(
            Collectors.toMap(
                pair ->
                    URLDecoder.decode(pair.substring(0, pair.indexOf('=')), StandardCharsets.UTF_8),
                pair ->
                    URLDecoder.decode(
                        pair.substring(pair.indexOf('=') + 1), StandardCharsets.UTF_8),
                (a, b) -> b));
  }
}
