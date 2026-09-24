package org.conference.registration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.conference.registration.api.dto.ApiResponses.ErrorResponse;
import org.conference.registration.service.RateLimiter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-IP rate limiting (AC-PC-05) and request size cap for registration submissions. It is
 * registered inside the Spring Security chain after the header writer, so 429/413 responses also
 * carry the security headers. The client IP is {@code getRemoteAddr()}, which Tomcat's
 * RemoteIpValve derives from {@code X-Forwarded-For} only when the request came from a trusted
 * (private-network) proxy.
 */
@Component("rateLimitFilter")
public class RateLimitFilter extends OncePerRequestFilter {

  static final long MAX_REGISTRATION_BYTES = 16 * 1024;

  private final RateLimiter rateLimiter;
  private final ObjectMapper objectMapper;

  public RateLimitFilter(RateLimiter rateLimiter, ObjectMapper objectMapper) {
    this.rateLimiter = rateLimiter;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    RateLimiter.Bucket bucket = bucketFor(request.getMethod(), path);
    if (bucket == RateLimiter.Bucket.REGISTRATION
        && request.getContentLengthLong() > MAX_REGISTRATION_BYTES) {
      write(response, 413, new ErrorResponse(413, "PAYLOAD_TOO_LARGE", "Request is too large."));
      return;
    }
    if (bucket != null) {
      RateLimiter.Decision decision = rateLimiter.tryAcquire(bucket, request.getRemoteAddr());
      if (!decision.allowed()) {
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        write(
            response,
            429,
            new ErrorResponse(
                429, "RATE_LIMITED", "Too many requests. Please wait a moment and try again."));
        return;
      }
    }
    chain.doFilter(request, response);
  }

  static RateLimiter.Bucket bucketFor(String method, String path) {
    if ("POST".equals(method) && path.startsWith("/api/registrations/")) {
      return RateLimiter.Bucket.REGISTRATION;
    }
    if ("GET".equals(method) && "/api/form-token".equals(path)) {
      return RateLimiter.Bucket.FORM_TOKEN;
    }
    if (path.startsWith("/api/admin/")) {
      return RateLimiter.Bucket.ADMIN;
    }
    return null;
  }

  private void write(HttpServletResponse response, int status, ErrorResponse body)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
