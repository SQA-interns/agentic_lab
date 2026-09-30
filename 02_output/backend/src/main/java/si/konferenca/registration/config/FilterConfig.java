package si.konferenca.registration.config;

import java.time.Clock;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import si.konferenca.registration.settings.AppProperties;
import si.konferenca.registration.web.OrganizerHttpsFilter;
import si.konferenca.registration.web.RateLimitFilter;
import si.konferenca.registration.web.RequestSizeFilter;

/** Registers the API filters ahead of Spring Security (order -100), so they run before login. */
@Configuration
public class FilterConfig {

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilter(AppProperties properties, Clock clock) {
    FilterRegistrationBean<RateLimitFilter> bean =
        new FilterRegistrationBean<>(new RateLimitFilter(properties.rateLimit(), clock));
    bean.addUrlPatterns("/api/*");
    bean.setOrder(-300);
    return bean;
  }

  @Bean
  FilterRegistrationBean<RequestSizeFilter> requestSizeFilter(AppProperties properties) {
    FilterRegistrationBean<RequestSizeFilter> bean =
        new FilterRegistrationBean<>(new RequestSizeFilter(properties.maxRequestBytes()));
    bean.addUrlPatterns("/api/*");
    bean.setOrder(-250);
    return bean;
  }

  @Bean
  FilterRegistrationBean<OrganizerHttpsFilter> organizerHttpsFilter(AppProperties properties) {
    FilterRegistrationBean<OrganizerHttpsFilter> bean =
        new FilterRegistrationBean<>(new OrganizerHttpsFilter());
    bean.addUrlPatterns("/api/organizer/*");
    bean.setOrder(-200);
    bean.setEnabled(properties.organizer().httpsOnly());
    return bean;
  }
}
