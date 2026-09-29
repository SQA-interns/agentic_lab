package si.konferenca.registration.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import java.io.IOException;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.integration.ExcelExportWriter;
import si.konferenca.registration.integration.JsonBackupStore;
import si.konferenca.registration.integration.MailNotifier;
import si.konferenca.registration.integration.OptionsFileReader;
import si.konferenca.registration.integration.RecaptchaVerifier;
import si.konferenca.registration.persistence.ConferenceOptionRepository;
import si.konferenca.registration.persistence.RegistrationRepository;
import si.konferenca.registration.service.OptionCatalogService;
import si.konferenca.registration.service.OrganizerService;
import si.konferenca.registration.service.RegistrationService;
import si.konferenca.registration.service.RegistrationValidator;

/** Wires services and adapters from {@link AppProperties} (the only place that reads settings). */
@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class ApplicationConfig {

  private static final Logger LOG = LoggerFactory.getLogger(ApplicationConfig.class);

  /** Maximum length of any JSON string value accepted by the API (specification §6). */
  private static final int MAX_JSON_STRING_LENGTH = 16 * 1024;

  private static final int MAX_JSON_NESTING_DEPTH = 10;

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
    return new TransactionTemplate(transactionManager);
  }

  @Bean
  Jackson2ObjectMapperBuilderCustomizer jsonReadLimits() {
    return builder ->
        builder.postConfigurer(
            mapper ->
                mapper
                    .getFactory()
                    .setStreamReadConstraints(
                        StreamReadConstraints.builder()
                            .maxStringLength(MAX_JSON_STRING_LENGTH)
                            .maxNestingDepth(MAX_JSON_NESTING_DEPTH)
                            .build()));
  }

  @Bean
  RecaptchaVerifier recaptchaVerifier(AppProperties properties) {
    AppProperties.Recaptcha recaptcha = properties.recaptcha();
    recaptcha.requireKeysUnlessTestMode();
    if (recaptcha.testMode()) {
      LOG.warn("reCAPTCHA TEST MODE is enabled - never use this setting in production");
    }
    return new RecaptchaVerifier(
        recaptcha.testMode(), recaptcha.secretKey(), recaptcha.verifyUrl());
  }

  @Bean
  OptionsFileReader optionsFileReader(AppProperties properties) {
    return new OptionsFileReader(properties.options().file());
  }

  @Bean
  JsonBackupStore jsonBackupStore(AppProperties properties) throws IOException {
    JsonBackupStore store = new JsonBackupStore(properties.backup().dir());
    store.initialize();
    return store;
  }

  @Bean
  MailNotifier mailNotifier(JavaMailSender sender, AppProperties properties) {
    return new MailNotifier(
        sender,
        properties.mail().from(),
        properties.organizer().emails(),
        properties.conferenceName());
  }

  @Bean
  ExcelExportWriter excelExportWriter() {
    return new ExcelExportWriter();
  }

  @Bean
  RegistrationValidator registrationValidator() {
    return new RegistrationValidator();
  }

  @Bean
  OptionCatalogService optionCatalogService(
      OptionsFileReader reader, ConferenceOptionRepository repository, TransactionTemplate tx) {
    return new OptionCatalogService(reader, repository, tx);
  }

  /** Loads the options file at startup; a missing or invalid file stops the application. */
  @Bean
  ApplicationRunner loadConferenceOptions(OptionCatalogService catalog) {
    return args -> catalog.initialize();
  }

  @Bean
  RegistrationService registrationService(
      RecaptchaVerifier recaptcha,
      RegistrationValidator validator,
      OptionCatalogService catalog,
      RegistrationRepository registrations,
      ConferenceOptionRepository options,
      JsonBackupStore backups,
      MailNotifier mail,
      TransactionTemplate tx,
      Clock clock) {
    return new RegistrationService(
        recaptcha, validator, catalog, registrations, options, backups, mail, tx, clock);
  }

  @Bean
  OrganizerService organizerService(
      RegistrationRepository registrations,
      ConferenceOptionRepository options,
      ExcelExportWriter excel,
      JsonBackupStore backups,
      TransactionTemplate tx) {
    return new OrganizerService(registrations, options, excel, backups, tx);
  }
}
