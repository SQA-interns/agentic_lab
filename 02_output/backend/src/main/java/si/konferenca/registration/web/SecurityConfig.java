package si.konferenca.registration.web;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.List;
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
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import si.konferenca.registration.config.AppProperties;

/**
 * Public registration paths, organizer-only export (BR-08, SB-02, D-19) and security headers
 * (SB-10); everything else is refused.
 */
@Configuration
public class SecurityConfig {

  static final String EXPORT_PATHS = "/api/export/**";
  private static final String ORGANIZER = "ORGANIZER";

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, AppProperties app) throws Exception {
    AuthenticationEntryPoint basicChallenge =
        (request, response, e) -> {
          response.setHeader("WWW-Authenticate", "Basic realm=\"organizer\"");
          ErrorResponses.write(response, 401, "UNAUTHORIZED", ErrorResponses.UNAUTHORIZED);
        };
    AuthenticationEntryPoint notFound =
        (request, response, e) -> response.sendError(HttpServletResponse.SC_NOT_FOUND);
    RequestMatcher export = request -> request.getRequestURI().startsWith("/api/export/");

    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .securityContext(context -> context.requireExplicitSave(true))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(HttpMethod.GET, "/api/form-config")
                    .permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/registrations")
                    .permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/export/registrations.xlsx")
                    .hasRole(ORGANIZER)
                    .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/error")
                    .permitAll()
                    .anyRequest()
                    .denyAll())
        .httpBasic(basic -> basic.realmName("organizer").authenticationEntryPoint(basicChallenge))
        .exceptionHandling(
            e ->
                e.defaultAuthenticationEntryPointFor(basicChallenge, export)
                    .defaultAuthenticationEntryPointFor(notFound, AnyRequestMatcher.INSTANCE)
                    .accessDeniedHandler(
                        (request, response, ex) ->
                            response.sendError(HttpServletResponse.SC_NOT_FOUND)))
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
    http.addFilterBefore(
            new RequestLimitsFilter(app, Clock.systemUTC()), BasicAuthenticationFilter.class)
        .addFilterAfter(
            new HttpsOnlyFilter(app.organizer().httpsOnly()), RequestLimitsFilter.class);
    if (app.corsAllowedOrigin() != null && !app.corsAllowedOrigin().isBlank()) {
      http.cors(cors -> cors.configurationSource(corsSource(app.corsAllowedOrigin())));
    }
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);
  }

  /** The one organizer account; only the BCrypt hash of its password is kept (SB-03). */
  @Bean
  UserDetailsService organizerAccount(AppProperties app, PasswordEncoder encoder) {
    return new InMemoryUserDetailsManager(
        User.withUsername(app.organizer().username())
            .password(encoder.encode(app.organizer().password()))
            .roles(ORGANIZER)
            .build());
  }

  private static CorsConfigurationSource corsSource(String origin) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(origin));
    cors.setAllowedMethods(List.of("GET", "POST"));
    cors.setAllowedHeaders(List.of("Content-Type", "Authorization"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", cors);
    return source;
  }
}
