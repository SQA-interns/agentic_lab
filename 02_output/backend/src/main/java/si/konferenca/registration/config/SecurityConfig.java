package si.konferenca.registration.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless API: the public operations are open, the organizer operations check their own bearer
 * token (specification section 6), everything else is denied. Security headers per SB-10.
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http, AppProperties properties) {
    try {
      http.csrf(AbstractHttpConfigurer::disable)
          .httpBasic(AbstractHttpConfigurer::disable)
          .formLogin(AbstractHttpConfigurer::disable)
          .logout(AbstractHttpConfigurer::disable)
          .requestCache(AbstractHttpConfigurer::disable)
          .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
          .authorizeHttpRequests(
              auth ->
                  auth.requestMatchers(HttpMethod.GET, "/api/form-config")
                      .permitAll()
                      .requestMatchers(HttpMethod.POST, "/api/registrations")
                      .permitAll()
                      .requestMatchers(HttpMethod.POST, "/api/organizer/token")
                      .permitAll()
                      .requestMatchers(HttpMethod.GET, "/api/organizer/registrations/export")
                      .permitAll()
                      .requestMatchers("/actuator/health", "/actuator/health/**")
                      .permitAll()
                      .anyRequest()
                      .denyAll())
          .headers(
              h ->
                  h.contentSecurityPolicy(
                          csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                      .frameOptions(f -> f.deny())
                      .referrerPolicy(
                          r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)));
      String origin = properties.corsAllowedOrigin();
      if (origin != null && !origin.isBlank()) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(List.of(origin.strip()));
        cors.setAllowedMethods(List.of("GET", "POST"));
        cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        http.cors(c -> c.configurationSource(source));
      }
      return http.build();
    } catch (Exception e) {
      throw new IllegalStateException("security configuration failed", e);
    }
  }

  /** No framework users: stops Spring Boot from generating and logging a default password. */
  @Bean
  UserDetailsService noUsers() {
    return username -> {
      throw new UsernameNotFoundException("no users");
    };
  }
}
