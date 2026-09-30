package si.konferenca.registration.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import si.konferenca.registration.settings.AppProperties;
import si.konferenca.registration.web.ErrorBody;
import si.konferenca.registration.web.ErrorResponses;

/**
 * HTTP security (02_specification.md 5.1, 5.6): stateless, public participant endpoints, the
 * organizer export behind HTTP Basic, everything else denied, security headers on every response.
 */
@Configuration
public class SecurityConfig {

  static final String ORGANIZER = "ORGANIZER";

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties properties)
      throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .cors(Customizer.withDefaults())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .headers(
            h ->
                h.contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .frameOptions(f -> f.deny())
                    .referrerPolicy(
                        r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .httpBasic(
            basic ->
                basic
                    .realmName("organizer")
                    .authenticationEntryPoint(
                        (request, response, e) -> {
                          response.setHeader("WWW-Authenticate", "Basic realm=\"organizer\"");
                          ErrorResponses.write(
                              response,
                              ErrorBody.of(401, "unauthorized", "Organizer login required."));
                        }))
        .exceptionHandling(
            ex ->
                ex.accessDeniedHandler(
                    (request, response, e) ->
                        ErrorResponses.write(
                            response, ErrorBody.of(403, "forbidden", "Access denied."))))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/health", "/api/health/**", "/error")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/options", "/api/config")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/registrations")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/organizer/**")
                    .hasRole(ORGANIZER)
                    .anyRequest()
                    .denyAll());
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** The single organizer account; only the BCrypt hash of the password is kept (SB-03). */
  @Bean
  UserDetailsService organizerAccount(
      AppProperties properties, PasswordEncoder encoder, StartupChecks checkedFirst) {
    AppProperties.Organizer organizer = properties.organizer();
    return new InMemoryUserDetailsManager(
        User.withUsername(organizer.username())
            .password(encoder.encode(organizer.password()))
            .roles(ORGANIZER)
            .build());
  }

  /** Cross-origin access only for explicitly configured origins (local development). */
  @Bean
  CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    List<String> origins = properties.cors().allowedOrigins();
    if (!origins.isEmpty()) {
      CorsConfiguration cors = new CorsConfiguration();
      cors.setAllowedOrigins(origins);
      cors.setAllowedMethods(List.of("GET", "POST"));
      cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
      source.registerCorsConfiguration("/api/**", cors);
    }
    return source;
  }
}
