package si.konferenca.registration.config;

import java.time.Clock;
import java.util.Map;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import si.konferenca.registration.api.RequestLimitsFilter;
import tools.jackson.databind.json.JsonMapper;

/** Registers the rate and size limits ahead of every other filter (SR-03). */
@Configuration
public class WebConfig {

  @Bean
  FilterRegistrationBean<RequestLimitsFilter> requestLimitsFilter(
      AppProperties properties, JsonMapper mapper, Clock clock) {
    AppProperties.RateLimit limits = properties.rateLimit();
    RequestLimitsFilter filter =
        new RequestLimitsFilter(
            Map.of(
                RequestLimitsFilter.Group.REGISTRATIONS, limits.registrations(),
                RequestLimitsFilter.Group.TOKENS, limits.tokens(),
                RequestLimitsFilter.Group.EXPORTS, limits.exports(),
                RequestLimitsFilter.Group.FORM_CONFIG, limits.formConfig()),
            properties.maxRequestBytes(),
            mapper,
            clock);
    FilterRegistrationBean<RequestLimitsFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    registration.addUrlPatterns("/api/*");
    return registration;
  }
}
