package si.konferenca.registration.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/**
 * Per-client rate limits in fixed one-minute windows and a request body limit for the API (SR-03,
 * SB-06). The client is the remote address after the trusted proxy's forwarding headers.
 */
public class RequestLimitsFilter extends OncePerRequestFilter {

  /** Endpoint groups with their own limit. */
  public enum Group {
    REGISTRATIONS,
    TOKENS,
    EXPORTS,
    FORM_CONFIG
  }

  private static final long WINDOW_MILLIS = 60_000;
  private static final int MAX_TRACKED = 10_000;

  private final Map<Group, Integer> limits;
  private final int maxRequestBytes;
  private final JsonMapper mapper;
  private final Clock clock;
  private final Map<String, Window> windows = new ConcurrentHashMap<>();

  private record Window(long start, int count) {}

  public RequestLimitsFilter(
      Map<Group, Integer> limits, int maxRequestBytes, JsonMapper mapper, Clock clock) {
    this.limits = Map.copyOf(limits);
    this.maxRequestBytes = maxRequestBytes;
    this.mapper = mapper;
    this.clock = clock;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Group group = group(request);
    if (group != null && !allow(group, request.getRemoteAddr())) {
      response.setHeader("Retry-After", String.valueOf(WINDOW_MILLIS / 1000));
      write(
          response,
          Problem.of(
              HttpStatus.TOO_MANY_REQUESTS,
              "RATE_LIMITED",
              "Too many attempts. Please wait a minute and try again."));
      return;
    }
    if (request.getContentLengthLong() > maxRequestBytes) {
      write(response, tooLarge());
      return;
    }
    HttpServletRequest limited = request;
    if ("POST".equals(request.getMethod()) && request.getContentLengthLong() < 0) {
      byte[] body = request.getInputStream().readNBytes(maxRequestBytes + 1);
      if (body.length > maxRequestBytes) {
        write(response, tooLarge());
        return;
      }
      limited = new CachedBodyRequest(request, body);
    }
    chain.doFilter(limited, response);
  }

  static Group group(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    if ("POST".equals(method) && "/api/registrations".equals(path)) {
      return Group.REGISTRATIONS;
    }
    if ("POST".equals(method) && "/api/organizer/token".equals(path)) {
      return Group.TOKENS;
    }
    if ("GET".equals(method) && "/api/organizer/registrations/export".equals(path)) {
      return Group.EXPORTS;
    }
    if ("GET".equals(method) && "/api/form-config".equals(path)) {
      return Group.FORM_CONFIG;
    }
    return null;
  }

  private boolean allow(Group group, String client) {
    long now = clock.millis();
    if (windows.size() > MAX_TRACKED) {
      windows.values().removeIf(w -> now - w.start() >= WINDOW_MILLIS);
    }
    Window window =
        windows.compute(
            group + "|" + client,
            (key, current) ->
                current == null || now - current.start() >= WINDOW_MILLIS
                    ? new Window(now, 1)
                    : new Window(current.start(), current.count() + 1));
    return window.count() <= limits.getOrDefault(group, Integer.MAX_VALUE);
  }

  private static Problem tooLarge() {
    return Problem.of(
        HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "The request is too large.");
  }

  private void write(HttpServletResponse response, Problem problem) throws IOException {
    response.setStatus(problem.status());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getOutputStream().write(mapper.writeValueAsBytes(problem));
  }

  /** A request whose body was already read (bounded) into memory. */
  private static final class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body.clone();
    }

    @Override
    public ServletInputStream getInputStream() {
      InputStream in = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() throws IOException {
          return in.read();
        }

        @Override
        public boolean isFinished() {
          return false;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("synchronous body only");
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      return new BufferedReader(
          new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }
  }
}
