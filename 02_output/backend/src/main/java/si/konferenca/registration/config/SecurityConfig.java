package si.konferenca.registration.config;

import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import si.konferenca.registration.adapter.in.web.ClientRequests;
import si.konferenca.registration.adapter.in.web.ExportController;
import si.konferenca.registration.adapter.in.web.HttpsRequiredFilter;
import si.konferenca.registration.adapter.in.web.ProblemWriter;
import si.konferenca.registration.adapter.in.web.RateLimitFilter;

/** Security controls of the specification (section 6). */
@Configuration
public class SecurityConfig {

  private static final String ORGANIZER_ROLE = "ORGANIZER";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** The single organizer account; only the bcrypt hash of its password is kept (SB-03). */
  @Bean
  UserDetailsService organizerAccount(AppProperties properties, PasswordEncoder encoder) {
    return new InMemoryUserDetailsManager(
        User.withUsername(properties.organizer().username())
            .password(encoder.encode(properties.organizer().password()))
            .roles(ORGANIZER_ROLE)
            .build());
  }

  @Bean
  ClientRequests clientRequests(AppProperties properties) {
    return new ClientRequests(properties.trustForwardedHeaders());
  }

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http, AppProperties properties, ClientRequests clientRequests, Clock clock)
      throws Exception {
    RateLimitFilter rateLimitFilter =
        new RateLimitFilter(
            new RateLimitFilter.Limits(
                properties.rateLimit().registration(),
                properties.rateLimit().export(),
                properties.rateLimit().read()),
            clientRequests,
            clock);
    AuthenticationEntryPoint organizerLoginRequired =
        (request, response, exception) -> {
          response.setHeader("WWW-Authenticate", "Basic realm=\"organizer\", charset=\"UTF-8\"");
          ProblemWriter.write(response, HttpStatus.UNAUTHORIZED.value());
        };
    http
        // No cookie or session exists, so there is nothing for CSRF protection to protect.
        .csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .cors(cors -> cors.configurationSource(corsConfigurationSource(properties)))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers(ExportController.PATH)
                    .hasRole(ORGANIZER_ROLE)
                    .anyRequest()
                    .permitAll())
        .httpBasic(basic -> basic.authenticationEntryPoint(organizerLoginRequired))
        .exceptionHandling(
            exceptions -> exceptions.authenticationEntryPoint(organizerLoginRequired))
        // Order: rate limit, then the HTTPS rule, then the credentials.
        .addFilterBefore(rateLimitFilter, BasicAuthenticationFilter.class)
        .addFilterBefore(
            new HttpsRequiredFilter(properties.organizer().requireHttps(), clientRequests),
            BasicAuthenticationFilter.class);
    return http.build();
  }

  private static CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    String origin = properties.corsAllowedOrigin();
    if (origin != null && !origin.isBlank()) {
      CorsConfiguration configuration = new CorsConfiguration();
      configuration.setAllowedOrigins(List.of(origin.strip()));
      configuration.setAllowedMethods(List.of("GET", "POST"));
      configuration.setAllowedHeaders(List.of("Content-Type", "Authorization"));
      source.registerCorsConfiguration("/api/**", configuration);
    }
    return source;
  }
}
