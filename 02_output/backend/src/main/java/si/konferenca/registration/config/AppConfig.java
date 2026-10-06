package si.konferenca.registration.config;

import java.nio.file.Path;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import si.konferenca.registration.application.FormSettings;
import si.konferenca.registration.domain.ConferenceCatalog;
import si.konferenca.registration.infrastructure.config.ConferenceConfigFile;
import si.konferenca.registration.web.RateLimitFilter;
import si.konferenca.registration.web.RequestSizeLimitFilter;

/** Settings, catalog and request filters. */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AppConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** Options and consents from CONFERENCE_CONFIG_FILE, read once at startup (AR-04). */
  @Bean
  ConferenceCatalog conferenceCatalog(AppProperties properties) {
    StartupGuard.check(properties);
    return ConferenceConfigFile.load(Path.of(properties.conferenceConfigFile()));
  }

  @Bean
  FormSettings formSettings(AppProperties properties) {
    return new FormSettings(
        properties.conferenceName(),
        properties.recaptcha().testMode(),
        properties.recaptcha().siteKey());
  }

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilter(AppProperties properties, Clock clock) {
    AppProperties.Limits limits = properties.limits();
    FilterRegistrationBean<RateLimitFilter> registration =
        new FilterRegistrationBean<>(
            new RateLimitFilter(
                limits.registrationsPerMinute(),
                limits.exportsPerMinute(),
                limits.formConfigPerMinute(),
                clock));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }

  @Bean
  FilterRegistrationBean<RequestSizeLimitFilter> requestSizeLimitFilter(AppProperties properties) {
    FilterRegistrationBean<RequestSizeLimitFilter> registration =
        new FilterRegistrationBean<>(
            new RequestSizeLimitFilter(properties.limits().maxRequestBytes()));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return registration;
  }
}
