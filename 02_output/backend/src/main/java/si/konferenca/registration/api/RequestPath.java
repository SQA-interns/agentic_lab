package si.konferenca.registration.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.util.UrlPathHelper;

/**
 * The request path as Spring MVC and Spring Security see it: percent-decoded, without path
 * parameters and context path. Filters must match on this, never on the raw request URI, or an
 * encoded path such as {@code /api/%65xport} would skip them (F-02).
 */
final class RequestPath {

  private RequestPath() {}

  static String of(HttpServletRequest request) {
    return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
  }
}
