package lab.conference.platform;

import java.time.Clock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * HTTP Basic for the organizer export only (AR-06, SB-02); everything else is public. Stateless: no
 * session, no JWT. CSRF stays on except for the credential-less registration POSTs, which are
 * protected by the Origin allow-list and JSON-only content type instead.
 */
@Configuration
public class SecurityConfig {

  public static final String REALM = "Lab Conference organizer";
  private static final Pattern BCRYPT =
      Pattern.compile("^\\{bcrypt\\}\\$2[aby]?\\$(\\d\\d)\\$.{53}$");
  private static final Pattern PBKDF2 =
      Pattern.compile("^\\{pbkdf2(@[A-Za-z0-9_]+)?\\}[0-9a-fA-F]+$");

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/api/organizer/**")
                    .hasRole("ORGANIZER")
                    .requestMatchers("/actuator/health", "/actuator/health/**")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    .anyRequest()
                    .permitAll())
        .httpBasic(
            basic ->
                basic
                    .realmName(REALM)
                    .authenticationEntryPoint(
                        (request, response, e) -> {
                          response.setHeader(
                              "WWW-Authenticate",
                              "Basic realm=\"" + REALM + "\", charset=\"UTF-8\"");
                          Problems.write(response, HttpStatus.UNAUTHORIZED, "Unauthorized");
                        }))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.ignoringRequestMatchers("/api/registrations/**"))
        .formLogin(f -> f.disable())
        .logout(l -> l.disable())
        .requestCache(c -> c.disable())
        .headers(
            h ->
                h.contentSecurityPolicy(
                        c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                    .frameOptions(f -> f.deny())
                    .referrerPolicy(
                        r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .contentTypeOptions(Customizer.withDefaults())
                    .cacheControl(Customizer.withDefaults()));
    return http.build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Bean
  UserDetailsService organizer(AppProperties props) {
    AppProperties.Organizer o = props.organizer();
    if (o == null || isBlank(o.username()) || isBlank(o.passwordHash())) {
      throw new IllegalStateException("ORGANIZER_USERNAME and ORGANIZER_PASSWORD_HASH must be set");
    }
    requireSlowHash(o.passwordHash().trim());
    return new InMemoryUserDetailsManager(
        User.withUsername(o.username().trim())
            .password(o.passwordHash().trim())
            .roles("ORGANIZER")
            .build());
  }

  /** Accepts only {bcrypt} with cost >= 10 or {pbkdf2} (SB-03). */
  static void requireSlowHash(String hash) {
    Matcher bcrypt = BCRYPT.matcher(hash);
    if (bcrypt.matches()) {
      if (Integer.parseInt(bcrypt.group(1)) < 10) {
        throw new IllegalStateException("ORGANIZER_PASSWORD_HASH bcrypt cost must be at least 10");
      }
      return;
    }
    if (!PBKDF2.matcher(hash).matches()) {
      throw new IllegalStateException(
          "ORGANIZER_PASSWORD_HASH must be a {bcrypt} or {pbkdf2} hash");
    }
  }

  @Bean
  FilterRegistrationBean<RequestGuardFilter> requestGuardFilter(AppProperties props, Clock clock) {
    FilterRegistrationBean<RequestGuardFilter> bean =
        new FilterRegistrationBean<>(new RequestGuardFilter(props, clock));
    bean.setOrder(-200);
    bean.addUrlPatterns("/api/*");
    return bean;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }
}
