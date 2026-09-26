package si.konferenca.registration.web.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import si.konferenca.registration.config.AppProperties;
import si.konferenca.registration.web.filter.ErrorResponseWriter;
import si.konferenca.registration.web.filter.FixedWindowRateLimiter;
import si.konferenca.registration.web.filter.RegistrationRateLimitFilter;
import si.konferenca.registration.web.filter.RequestSizeLimitFilter;

/**
 * HTTP security: public registration endpoints, organizer-only export via HTTP Basic, security
 * headers, request-size limit and registration rate limit.
 */
@Configuration
public class SecurityConfig {

  static final String ORGANIZER_ROLE = "ORGANIZER";

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AppProperties properties,
      ObjectMapper objectMapper,
      Clock clock,
      CorsConfigurationSource corsConfigurationSource)
      throws Exception {
    ErrorResponseWriter errorWriter = new ErrorResponseWriter(objectMapper);
    AppProperties.RateLimit rateLimit = properties.rateLimit();
    FixedWindowRateLimiter limiter =
        new FixedWindowRateLimiter(rateLimit.maxRequests(), rateLimit.window(), clock);

    BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
    entryPoint.setRealmName("Organizer export");

    http.csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .httpBasic(basic -> basic.authenticationEntryPoint(entryPoint))
        .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .referrerPolicy(
                        r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .frameOptions(Customizer.withDefaults()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/conference")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/registrations")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/admin/registrations/export")
                    .hasRole(ORGANIZER_ROLE)
                    .requestMatchers(
                        HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .addFilterBefore(
            new RequestSizeLimitFilter(properties.request().maxBodyBytes(), errorWriter),
            BasicAuthenticationFilter.class)
        .addFilterBefore(
            new RegistrationRateLimitFilter(limiter, errorWriter), BasicAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public UserDetailsService organizerUsers(AppProperties properties, PasswordEncoder encoder) {
    AppProperties.Organizer organizer = properties.organizer();
    if (organizer == null
        || organizer.password() == null
        || organizer.password().isBlank()
        || organizer.username() == null
        || organizer.username().isBlank()) {
      return new InMemoryUserDetailsManager();
    }
    UserDetails user =
        User.withUsername(organizer.username())
            .password(encoder.encode(organizer.password()))
            .roles(ORGANIZER_ROLE)
            .build();
    return new InMemoryUserDetailsManager(user);
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    List<String> origins = properties.cors().allowedOrigins();
    if (!origins.isEmpty()) {
      CorsConfiguration config = new CorsConfiguration();
      config.setAllowedOrigins(origins);
      config.setAllowedMethods(List.of("GET", "POST"));
      config.setAllowedHeaders(List.of("Content-Type"));
      source.registerCorsConfiguration("/api/conference", config);
      source.registerCorsConfiguration("/api/registrations", config);
    }
    return source;
  }
}
