package si.konferenca.registration.integration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Raw HTTP calls for integration tests that need headers or bodies the API client cannot send. */
final class HttpSupport {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private HttpSupport() {}

  static HttpRequest.Builder request(int port, String path) {
    return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
  }

  static String basic(String user, String password) {
    return "Basic "
        + Base64.getEncoder()
            .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
  }

  static HttpResponse<String> send(HttpRequest.Builder request) {
    try {
      return CLIENT.send(
          request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
