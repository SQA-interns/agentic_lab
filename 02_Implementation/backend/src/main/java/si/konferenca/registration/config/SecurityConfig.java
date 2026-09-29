package si.konferenca.registration.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * HTTP security (specification §8): stateless, HTTP Basic only for organizer endpoints, security
 * headers on every response, rate limiting and body-size limits inside the security chain.
 */
@Configuration
public class SecurityConfig {

  static final String ORGANIZER_ROLE = "ORGANIZER";
  static final int MAX_BODY_BYTES = 16 * 1024;
  private static final String REALM = "organizer";

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** The single organizer account; only the BCrypt hash of the configured password is kept. */
  @Bean
  UserDetailsService organizerAccount(AppProperties properties, PasswordEncoder encoder) {
    AppProperties.Organizer organizer = properties.organizer();
    return new InMemoryUserDetailsManager(
        User.withUsername(organizer.username())
            .password(encoder.encode(organizer.password()))
            .roles(ORGANIZER_ROLE)
            .build());
  }

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties properties, Clock clock)
      throws Exception {
    RateLimitFilter rateLimit =
        new RateLimitFilter(
            properties.rateLimit().registrationPerMinute(),
            properties.rateLimit().organizerPerMinute(),
            clock);
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .httpBasic(
            basic ->
                basic
                    .realmName(REALM)
                    .authenticationEntryPoint(
                        (request, response, e) -> {
                          response.setHeader("WWW-Authenticate", "Basic realm=\"" + REALM + "\"");
                          ProblemResponses.write(
                              response,
                              401,
                              "Unauthorized",
                              "UNAUTHORIZED",
                              "Organizer credentials are required.");
                        }))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/organizer/**")
                    .hasRole(ORGANIZER_ROLE)
                    .anyRequest()
                    .permitAll())
        .headers(
            headers ->
                headers
                    .contentTypeOptions(c -> {})
                    .frameOptions(f -> f.deny())
                    .cacheControl(c -> {})
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER)))
        .addFilterBefore(rateLimit, BasicAuthenticationFilter.class)
        .addFilterAfter(new RequestSizeLimitFilter(MAX_BODY_BYTES), RateLimitFilter.class);
    return http.build();
  }
}
