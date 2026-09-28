package si.konferenca.registration.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
import si.konferenca.registration.config.AppProperties;

/**
 * HTTP security: organizer-only export via HTTP Basic, public registration API, stateless, and
 * security headers.
 */
@Configuration
public class SecurityConfig {

  static final String ORGANIZER_ROLE = "ORGANIZER";

  private static final Logger LOG = LoggerFactory.getLogger(SecurityConfig.class);

  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // Stateless API without cookie/session authentication: CSRF tokens are not applicable.
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .httpBasic(basic -> basic.realmName("organizer"))
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/organizer/**")
                    .hasRole(ORGANIZER_ROLE)
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/api/**", "/error")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .headers(
            headers ->
                headers
                    .contentTypeOptions(Customizer.withDefaults())
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(
                        referrer ->
                            referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .httpStrictTransportSecurity(
                        hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31_536_000)));
    return http.build();
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public UserDetailsService organizerUserDetailsService(
      AppProperties properties, PasswordEncoder passwordEncoder) {
    AppProperties.Organizer organizer = properties.organizer();
    InMemoryUserDetailsManager manager = new InMemoryUserDetailsManager();
    if (organizer.password() == null || organizer.password().isBlank()) {
      LOG.warn("ORGANIZER_PASSWORD is not set; the registration export is disabled");
      return manager;
    }
    manager.createUser(
        User.withUsername(organizer.username())
            .password(passwordEncoder.encode(organizer.password()))
            .roles(ORGANIZER_ROLE)
            .build());
    return manager;
  }
}
