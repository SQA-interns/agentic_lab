package si.konferenca.registration.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import si.konferenca.registration.api.OrganizerHttpsFilter;
import si.konferenca.registration.api.RequestSizeFilter;

/** Servlet filters that run before Spring Security (SR-03, SR-06). */
@Configuration(proxyBeanMethods = false)
public class WebConfiguration {

  @Bean
  FilterRegistrationBean<RequestSizeFilter> requestSizeFilter(AppProperties properties) {
    FilterRegistrationBean<RequestSizeFilter> registration =
        new FilterRegistrationBean<>(new RequestSizeFilter(properties.limits().maxRequestBytes()));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    return registration;
  }

  @Bean
  FilterRegistrationBean<OrganizerHttpsFilter> organizerHttpsFilter(AppProperties properties) {
    FilterRegistrationBean<OrganizerHttpsFilter> registration =
        new FilterRegistrationBean<>(new OrganizerHttpsFilter(properties.organizer().httpsOnly()));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 30);
    return registration;
  }
}
