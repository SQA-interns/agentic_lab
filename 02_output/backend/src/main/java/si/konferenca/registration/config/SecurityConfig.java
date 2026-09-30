package si.konferenca.registration.config;

import java.time.Clock;
import java.util.List;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Organizer access, headers and CORS (SB-02, SB-03, SB-10, SR-06); abuse filters (SR-03, SB-06) run
 * before Spring Security so they apply before any credential is processed.
 */
@Configuration
public class SecurityConfig {

  static final String ORGANIZER_PATHS = "/api/admin/**";
  private static final int FILTER_ORDER = -200;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties properties)
      throws Exception {
    http
        // Stateless JSON API without cookies or sessions: no CSRF token is needed.
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(c -> c.configurationSource(corsSource(properties)))
        .authorizeHttpRequests(
            a -> a.requestMatchers(ORGANIZER_PATHS).hasRole("ORGANIZER").anyRequest().permitAll())
        .httpBasic(b -> b.realmName("organizer"))
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .headers(
            h ->
                h.contentSecurityPolicy(
                        c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .frameOptions(f -> f.deny())
                    .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)));
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService organizerAccount(AppProperties properties, PasswordEncoder encoder) {
    return new InMemoryUserDetailsManager(
        User.withUsername(properties.organizer().username())
            .password(encoder.encode(properties.organizer().password()))
            .roles("ORGANIZER")
            .build());
  }

  @Bean
  FilterRegistrationBean<RequestSizeFilter> requestSizeFilter(AppProperties properties) {
    FilterRegistrationBean<RequestSizeFilter> bean =
        new FilterRegistrationBean<>(new RequestSizeFilter(properties.maxRequestBytes()));
    bean.setOrder(FILTER_ORDER);
    return bean;
  }

  @Bean
  FilterRegistrationBean<RateLimitFilter> rateLimitFilter(AppProperties properties, Clock clock) {
    FilterRegistrationBean<RateLimitFilter> bean =
        new FilterRegistrationBean<>(
            new RateLimitFilter(
                List.of(
                    new RateLimitFilter.Rule(
                        "registration",
                        HttpMethod.POST,
                        "/api/registrations",
                        properties.rateLimit().registrationPer10Min(),
                        java.time.Duration.ofMinutes(10)),
                    new RateLimitFilter.Rule(
                        "export",
                        null,
                        "/api/admin/",
                        properties.rateLimit().exportPerMin(),
                        java.time.Duration.ofMinutes(1))),
                clock));
    bean.setOrder(FILTER_ORDER + 1);
    return bean;
  }

  @Bean
  FilterRegistrationBean<OrganizerTransportFilter> organizerTransportFilter(
      AppProperties properties) {
    FilterRegistrationBean<OrganizerTransportFilter> bean =
        new FilterRegistrationBean<>(
            new OrganizerTransportFilter(properties.organizer().httpsOnly()));
    bean.setOrder(FILTER_ORDER + 2);
    return bean;
  }

  private static CorsConfigurationSource corsSource(AppProperties properties) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    List<String> origins = properties.cors().origins();
    if (!origins.isEmpty()) {
      CorsConfiguration config = new CorsConfiguration();
      config.setAllowedOrigins(origins);
      config.setAllowedMethods(List.of("GET", "POST"));
      config.setAllowedHeaders(List.of("Content-Type"));
      source.registerCorsConfiguration("/api/**", config);
    }
    return source;
  }
}
