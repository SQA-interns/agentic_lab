package si.konferenca.registration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Skeleton security: health is public, everything else is denied. Declaring a {@link
 * UserDetailsService} stops Spring Boot from generating and logging a default password.
 */
@Configuration
public class SkeletonSecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) {
    try {
      http.authorizeHttpRequests(
          auth -> auth.requestMatchers("/actuator/health/**").permitAll().anyRequest().denyAll());
      return http.build();
    } catch (Exception e) {
      throw new IllegalStateException("security configuration failed", e);
    }
  }

  @Bean
  UserDetailsService noUsers() {
    return username -> {
      throw new UsernameNotFoundException("no users");
    };
  }
}
