package si.konferenca.registration.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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
import si.konferenca.registration.api.ErrorResponse;
import si.konferenca.registration.api.ErrorResponseWriter;

/**
 * Access rules (BR-08, SB-02, SB-10): the form and registration are public, the export needs the
 * organizer role over HTTP Basic (D-20), everything else is denied.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

  static final String ORGANIZER_ROLE = "ORGANIZER";
  private static final String REALM = "organizer";

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    AuthenticationEntryPoint unauthorized =
        (request, response, exception) -> {
          response.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\"");
          ErrorResponseWriter.write(
              response,
              HttpServletResponse.SC_UNAUTHORIZED,
              ErrorResponse.of("unauthorized", "Organizer credentials are required."));
        };
    AccessDeniedHandler forbidden =
        (request, response, exception) ->
            ErrorResponseWriter.write(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                ErrorResponse.of("unauthorized", "Access is not allowed."));
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(HttpMethod.GET, "/api/registration-form")
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
        .httpBasic(basic -> basic.realmName(REALM).authenticationEntryPoint(unauthorized))
        .exceptionHandling(
            exceptions ->
                exceptions.authenticationEntryPoint(unauthorized).accessDeniedHandler(forbidden))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .frameOptions(frame -> frame.deny()));
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** The single organizer, with only a BCrypt hash of the configured password kept (SB-03). */
  @Bean
  UserDetailsService organizerUsers(AppProperties properties, PasswordEncoder encoder) {
    return new InMemoryUserDetailsManager(
        User.withUsername(properties.organizer().username())
            .password(encoder.encode(properties.organizer().password()))
            .roles(ORGANIZER_ROLE)
            .build());
  }
}
