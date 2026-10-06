package si.konferenca.registration.config;

import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import si.konferenca.registration.web.RateLimitFilter;
import si.konferenca.registration.web.RequestSizeFilter;

/** Size limit and rate limiting run before Spring Security (SR-03). */
@Configuration
public class WebFilterConfiguration {

  private static final int SECURITY_ORDER = -100;

  @Bean
  FilterRegistrationBean<RequestSizeFilter> requestSizeFilter(AppProperties p) {
    FilterRegistrationBean<RequestSizeFilter> r =
        new FilterRegistrationBean<>(new RequestSizeFilter(p.maxRequestBytes()));
    r.addUrlPatterns("/api/*");
    r.setOrder(SECURITY_ORDER - 20);
    return r;
  }

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilter(AppProperties p, Clock clock) {
    AppProperties.RateLimit l = p.rateLimit();
    FilterRegistrationBean<RateLimitFilter> r =
        new FilterRegistrationBean<>(
            new RateLimitFilter(
                l.registrationPerMinute(), l.exportPerMinute(), l.optionsPerMinute(), clock));
    r.addUrlPatterns("/api/*");
    r.setOrder(SECURITY_ORDER - 10);
    return r;
  }
}
