package org.conference.registration.config;

import jakarta.servlet.Filter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.HeaderWriterFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/** Backend preventive security controls (specification §11). */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  static final String ORGANIZER = "ORGANIZER";

  @Bean
  public SecurityFilterChain securityFilterChain(
      HttpSecurity http, @Qualifier("rateLimitFilter") Filter rateLimitFilter) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable) // stateless API, no cookies (spec §11)
        .cors(AbstractHttpConfigurer::disable) // same origin via reverse proxy
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .requestCache(AbstractHttpConfigurer::disable)
        .httpBasic(basic -> basic.realmName("Conference organizers"))
        .headers(
            headers ->
                headers
                    .contentSecurityPolicy(
                        csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .referrerPolicy(
                        ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .frameOptions(frame -> frame.deny()))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/options", "/api/form-token", "/api/registrations/**")
                    .permitAll()
                    .requestMatchers(
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/admin/registrations/export")
                    .hasRole(ORGANIZER)
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .addFilterAfter(rateLimitFilter, HeaderWriterFilter.class);
    return http.build();
  }

  /**
   * The rate-limit filter runs inside the security chain only, not a second time as servlet filter.
   */
  @Bean
  public FilterRegistrationBean<Filter> rateLimitFilterRegistration(
      @Qualifier("rateLimitFilter") Filter rateLimitFilter) {
    FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(rateLimitFilter);
    registration.setEnabled(false);
    return registration;
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public UserDetailsService organizerAccount(AppProperties properties, PasswordEncoder encoder) {
    AppProperties.Admin admin = properties.admin();
    return new InMemoryUserDetailsManager(
        User.withUsername(admin.username())
            .password(encoder.encode(admin.password()))
            .roles(ORGANIZER)
            .build());
  }
}
