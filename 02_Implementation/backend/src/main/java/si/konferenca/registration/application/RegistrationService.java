package si.konferenca.registration.application;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.application.port.CaptchaVerifier;
import si.konferenca.registration.application.port.RegistrationBackup;
import si.konferenca.registration.application.port.RegistrationNotifier;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.SelectedOption;
import si.konferenca.registration.persistence.RegistrationRepository;

/** Registration use case: validate, verify captcha, store (DB + JSON backup), then notify. */
@Service
public class RegistrationService {

  private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

  private final RegistrationValidator validator;
  private final CaptchaVerifier captchaVerifier;
  private final ConferenceCatalog catalog;
  private final RegistrationRepository repository;
  private final RegistrationBackup backup;
  private final RegistrationNotifier notifier;
  private final RegistrationJsonMapper jsonMapper;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  public RegistrationService(
      RegistrationValidator validator,
      CaptchaVerifier captchaVerifier,
      ConferenceCatalog catalog,
      RegistrationRepository repository,
      RegistrationBackup backup,
      RegistrationNotifier notifier,
      RegistrationJsonMapper jsonMapper,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.validator = validator;
    this.captchaVerifier = captchaVerifier;
    this.catalog = catalog;
    this.repository = repository;
    this.backup = backup;
    this.notifier = notifier;
    this.jsonMapper = jsonMapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.clock = clock;
  }

  public Registration register(RegistrationCommand command, String clientIp) {
    NormalizedRegistration input = validator.validate(command);
    captchaVerifier.verify(command.recaptchaToken(), clientIp);

    StoredRegistration stored = transactionTemplate.execute(status -> store(input));
    if (stored == null) {
      throw new IllegalStateException("Transaction returned no registration");
    }
    LOG.info(
        "Registration {} accepted (type {})",
        stored.registration().getId(),
        stored.registration().getType());

    notifier.registrationAccepted(stored.registration(), stored.json());
    return stored.registration();
  }

  private StoredRegistration store(NormalizedRegistration input) {
    Registration registration = buildRegistration(input);
    try {
      Registration saved = repository.saveAndFlush(registration);
      String json = jsonMapper.toJson(saved);
      backup.write(saved, json);
      return new StoredRegistration(saved, json);
    } catch (DataAccessException e) {
      throw new RegistrationStorageException("Could not store registration in database", e);
    } catch (IOException e) {
      throw new RegistrationStorageException("Could not write registration backup", e);
    }
  }

  private Registration buildRegistration(NormalizedRegistration input) {
    List<SelectedOption> options =
        input.optionIds().stream()
            .map(id -> catalog.findActiveOption(id).orElseThrow())
            .map(SelectedOption::of)
            .toList();
    return Registration.builder()
        .type(input.type())
        .firstName(input.firstName())
        .lastName(input.lastName())
        .email(input.email())
        .organization(input.organization())
        .studyInstitution(input.studyInstitution())
        .studyProgramme(input.studyProgramme())
        .studentId(input.studentId())
        .createdAt(Instant.now(clock).truncatedTo(ChronoUnit.MICROS))
        .selectedOptions(options)
        .consentIds(input.consentIds())
        .build();
  }

  private record StoredRegistration(Registration registration, String json) {}
}
