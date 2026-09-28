package org.example.conference;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Conference registration backend: single deployable (AR-02). */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ConferenceApplication {

  public static void main(String[] args) {
    SpringApplication.run(ConferenceApplication.class, args);
  }
}
