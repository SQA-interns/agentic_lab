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
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import si.konferenca.registration.web.HttpsOnlyFilter;
import si.konferenca.registration.web.Problems;

/** Organizer-only export, public registration, headers and CORS (spec section 6). */
@Configuration
public class SecurityConfiguration {

  static final String ORGANIZER = "ORGANIZER";

  @Bean
  SecurityFilterChain apiSecurity(HttpSecurity http, AppProperties p) throws Exception {
    AuthenticationEntryPoint unauthorized =
        (request, response, e) -> {
          response.setHeader("WWW-Authenticate", "Basic realm=\"organizer\"");
          Problems.write(
              response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
        };
    AccessDeniedHandler forbidden =
        (request, response, e) ->
            Problems.write(response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    http.csrf(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            a ->
                a.requestMatchers(HttpMethod.GET, "/api/options")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/registrations")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/admin/registrations/export")
                    .hasRole(ORGANIZER)
                    .requestMatchers("/actuator/health", "/actuator/health/**", "/error")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .httpBasic(b -> b.realmName("organizer").authenticationEntryPoint(unauthorized))
        .exceptionHandling(
            e -> e.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
        .headers(
            h ->
                h.contentSecurityPolicy(
                        c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .frameOptions(f -> f.deny())
                    .referrerPolicy(
                        r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
        .addFilterBefore(
            new HttpsOnlyFilter(p.organizer().httpsOnly()), BasicAuthenticationFilter.class);
    String origin = p.corsAllowedOrigin();
    if (origin == null || origin.isBlank()) {
      http.cors(AbstractHttpConfigurer::disable);
    } else {
      CorsConfiguration cors = new CorsConfiguration();
      cors.setAllowedOrigins(List.of(origin));
      cors.setAllowedMethods(List.of("GET", "POST"));
      cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
      UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
      source.registerCorsConfiguration("/api/**", cors);
      http.cors(c -> c.configurationSource(source));
    }
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** The configured organizer; the password is kept only as a BCrypt hash (SB-03). */
  @Bean
  UserDetailsService organizers(AppProperties p, PasswordEncoder encoder) {
    return new InMemoryUserDetailsManager(
        User.withUsername(p.organizer().username())
            .password(encoder.encode(p.organizer().password()))
            .roles(ORGANIZER)
            .build());
  }
}
