package si.konferenca.registration.config;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import si.konferenca.registration.web.Problems;

/**
 * Access control, organizer authentication and security headers (docs/02_specification.md §7, §11).
 */
@Configuration
public class SecurityConfig {

  static final String ORGANIZER = "ORGANIZER";
  private static final String REALM = "organizer";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** The single organizer; only the BCrypt hash of the password is kept (SB-03). */
  @Bean
  UserDetailsService organizers(AppProperties properties, PasswordEncoder encoder) {
    StartupGuard.check(properties);
    return new InMemoryUserDetailsManager(
        User.withUsername(properties.organizer().username())
            .password(encoder.encode(properties.organizer().password()))
            .roles(ORGANIZER)
            .build());
  }

  @Bean
  SecurityFilterChain api(HttpSecurity http, AppProperties properties) {
    AuthenticationEntryPoint unauthorized =
        (request, response, e) -> {
          response.setStatus(HttpStatus.UNAUTHORIZED.value());
          response.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\"");
          response.setContentType(Problems.PROBLEM_JSON.toString());
          response
              .getOutputStream()
              .write(
                  Problems.json(
                          HttpStatus.UNAUTHORIZED,
                          "Unauthorized",
                          "Organizer credentials are required.")
                      .getBytes(StandardCharsets.UTF_8));
        };
    AccessDeniedHandler forbidden =
        (request, response, e) -> {
          response.setStatus(HttpStatus.FORBIDDEN.value());
          response.setContentType(Problems.PROBLEM_JSON.toString());
          response
              .getOutputStream()
              .write(
                  Problems.json(HttpStatus.FORBIDDEN, "Forbidden", "Access is not allowed.")
                      .getBytes(StandardCharsets.UTF_8));
        };
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/form-config")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/registrations")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, OrganizerHttpsFilter.EXPORT_PATH)
                    .hasRole(ORGANIZER)
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .httpBasic(basic -> basic.realmName(REALM).authenticationEntryPoint(unauthorized))
        .exceptionHandling(
            ex -> ex.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
        .addFilterBefore(
            new OrganizerHttpsFilter(properties.organizer().httpsOnly()),
            BasicAuthenticationFilter.class)
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(
                                ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
    List<String> origins = properties.allowedOriginList();
    if (origins.isEmpty()) {
      http.cors(cors -> cors.disable());
    } else {
      http.cors(Customizer.withDefaults());
    }
    return http.build();
  }

  /** Cross-origin access only for the configured origins (local development only). */
  @Bean
  CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(properties.allowedOriginList());
    cors.setAllowedMethods(List.of("GET", "POST"));
    cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
  }
}
