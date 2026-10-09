package si.konferenca.registration.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import si.konferenca.registration.api.OrganizerHttpsFilter;
import si.konferenca.registration.api.Problems;
import si.konferenca.registration.api.RateLimitFilter;
import si.konferenca.registration.api.RequestSizeFilter;

/**
 * Organizer access, headers, CORS and request limits (specification section 6: BR-08, SB-02, SB-03,
 * SB-06, SB-10, SR-03, SR-06, KP-02).
 */
@Configuration
public class SecurityConfig {

  static final String ORGANIZER = "ORGANIZER";
  static final String API_CSP = "default-src 'none'; frame-ancestors 'none'";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** The single organizer; only the BCrypt hash of the password is kept (SB-03). */
  @Bean
  UserDetailsService organizers(AppSettings settings, PasswordEncoder encoder) {
    return new InMemoryUserDetailsManager(
        User.withUsername(settings.organizerUsername())
            .password(encoder.encode(settings.organizerPassword()))
            .roles(ORGANIZER)
            .build());
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, AppSettings settings)
      throws Exception {
    AuthenticationEntryPoint unauthorized =
        (request, response, e) -> {
          response.setHeader("WWW-Authenticate", "Basic realm=\"organizer\"");
          Problems.write(response, HttpStatus.UNAUTHORIZED);
        };
    AccessDeniedHandler forbidden =
        (request, response, e) -> Problems.write(response, HttpStatus.FORBIDDEN);

    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/registrations/export")
                    .hasRole(ORGANIZER)
                    .requestMatchers("/api/**", "/actuator/health", "/actuator/health/**", "/error")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .httpBasic(basic -> basic.realmName("organizer").authenticationEntryPoint(unauthorized))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
        .headers(
            h ->
                h.contentSecurityPolicy(csp -> csp.policyDirectives(API_CSP))
                    .frameOptions(f -> f.deny())
                    .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)))
        .addFilterBefore(
            new RequestSizeFilter(settings.maxRequestBytes()), BasicAuthenticationFilter.class)
        .addFilterBefore(
            new RateLimitFilter(
                settings.registrationsPerMinute(),
                settings.exportsPerMinute(),
                settings.formsPerMinute(),
                System::currentTimeMillis),
            BasicAuthenticationFilter.class);
    if (settings.organizerHttpsOnly()) {
      http.addFilterBefore(new OrganizerHttpsFilter(), BasicAuthenticationFilter.class);
    }
    if (settings.corsAllowedOrigin().isEmpty()) {
      http.cors(AbstractHttpConfigurer::disable);
    } else {
      CorsConfiguration cors = new CorsConfiguration();
      cors.setAllowedOrigins(List.of(settings.corsAllowedOrigin()));
      cors.setAllowedMethods(List.of("GET", "POST"));
      cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
      UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/api/**", cors);
      http.cors(c -> c.configurationSource(source));
    }
    return http.build();
  }
}
