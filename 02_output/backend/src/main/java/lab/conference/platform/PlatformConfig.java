package lab.conference.platform;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Shared infrastructure beans. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
public class PlatformConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  AppProfile appProfile(AppProperties props) {
    return AppProfile.parse(props.profile());
  }
}
