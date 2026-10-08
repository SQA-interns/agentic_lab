package si.konferenca.registration.config;

import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/** Access rules (SB-02, BR-08), organizer Basic authentication (SB-03) and headers (SB-10). */
@Configuration
public class SecurityConfig {

  static final String ORGANIZER_ROLE = "ORGANIZER";
  static final String REALM = "organizer";

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties properties)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/form")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/registrations")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/export")
                    .hasRole(ORGANIZER_ROLE)
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .httpBasic(basic -> basic.realmName(REALM).authenticationEntryPoint(unauthorized()))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(unauthorized()).accessDeniedHandler(forbidden()))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .frameOptions(frame -> frame.deny())
                    .contentTypeOptions(Customizer.withDefaults())
                    .referrerPolicy(
                        r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .cacheControl(cache -> cache.disable())
                    .addHeaderWriter(new StaticHeadersWriter("Cache-Control", "no-store")));
    if (!properties.corsAllowedOrigin().isBlank()) {
      http.cors(cors -> cors.configurationSource(cors(properties.corsAllowedOrigin())));
    }
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** The single organizer; the plain password is hashed here and not kept (SB-03). */
  @Bean
  UserDetailsService organizers(AppProperties properties, PasswordEncoder encoder) {
    AppProperties.Organizer organizer = properties.organizer();
    return new InMemoryUserDetailsManager(
        User.withUsername(organizer.username())
            .password(encoder.encode(organizer.password()))
            .roles(ORGANIZER_ROLE)
            .build());
  }

  private static CorsConfigurationSource cors(String origin) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(origin));
    config.setAllowedMethods(List.of("GET", "POST"));
    config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
  }

  private static AuthenticationEntryPoint unauthorized() {
    return (request, response, e) -> {
      response.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\"");
      writeProblem(response, 401, "Organizer authentication required");
    };
  }

  private static AccessDeniedHandler forbidden() {
    return (request, response, e) -> writeProblem(response, 403, "Access denied");
  }

  /** Writes a problem detail without internal information (SB-07). */
  public static void writeProblem(
      jakarta.servlet.http.HttpServletResponse response, int status, String title)
      throws IOException {
    response.setStatus(status);
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    response
        .getWriter()
        .write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + "}");
  }
}
