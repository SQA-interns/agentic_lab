package org.example.conference.shared.web;

import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

/** Registers request-size and rate-limit filters ahead of Spring Security. */
@Configuration(proxyBeanMethods = false)
public class WebFilterConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(
      WebLimitsProperties limits, ObjectMapper objectMapper) {
    FilterRegistrationBean<RequestSizeLimitFilter> bean =
        new FilterRegistrationBean<>(
            new RequestSizeLimitFilter(limits.maxBodyBytes(), objectMapper));
    bean.addUrlPatterns("/api/*");
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return bean;
  }

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(
      WebLimitsProperties limits, ObjectMapper objectMapper, Clock clock) {
    FilterRegistrationBean<RateLimitFilter> bean =
        new FilterRegistrationBean<>(
            new RateLimitFilter(limits.rateLimitPerMinute(), objectMapper, clock));
    bean.addUrlPatterns("/api/registrations/*");
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return bean;
  }
}
