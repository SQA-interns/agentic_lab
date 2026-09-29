package si.konferenca.registration.service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionDefinition;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.integration.OptionsFileReader;
import si.konferenca.registration.integration.OptionsFileReader.FileStamp;
import si.konferenca.registration.persistence.ConferenceOptionRepository;

/**
 * The configurable option catalog (US-003, specification §4). The options file is the source of
 * truth; it is synchronized into {@code conference_option} at startup and whenever the file's
 * last-modified time or size changes.
 */
public class OptionCatalogService {

  private static final Logger LOG = LoggerFactory.getLogger(OptionCatalogService.class);

  private final OptionsFileReader reader;
  private final ConferenceOptionRepository repository;
  private final TransactionTemplate transactions;
  private FileStamp lastSeen;

  public OptionCatalogService(
      OptionsFileReader reader,
      ConferenceOptionRepository repository,
      TransactionTemplate transactions) {
    this.reader = reader;
    this.repository = repository;
    this.transactions = transactions;
  }

  /** Startup synchronization; a missing or invalid options file stops the application. */
  public synchronized void initialize() throws IOException {
    FileStamp stamp = reader.stamp();
    synchronize(reader.read());
    lastSeen = stamp;
    LOG.info("Conference options loaded from {}", reader.file());
  }

  /** Re-synchronizes if the options file changed; keeps the previous catalog if it is invalid. */
  public synchronized void refreshIfChanged() {
    FileStamp stamp;
    try {
      stamp = reader.stamp();
    } catch (IOException e) {
      LOG.error("Options file {} is not readable; keeping the current options", reader.file());
      return;
    }
    if (stamp.equals(lastSeen)) {
      return;
    }
    lastSeen = stamp;
    try {
      synchronize(reader.read());
      LOG.info("Conference options reloaded from {}", reader.file());
    } catch (IOException | OptionsFileReader.InvalidOptionsFileException e) {
      LOG.error(
          "Options file {} is invalid ({}); keeping the current options",
          reader.file(),
          e.getMessage());
    }
  }

  /** Active options in display order: by set, then by position in the options file. */
  public List<ConferenceOption> activeOptions() {
    refreshIfChanged();
    return repository.findByActiveTrue().stream().sorted(Registration.OPTION_ORDER).toList();
  }

  /** Every known option (active and inactive) by id, as of now. */
  public Map<String, ConferenceOption> catalog() {
    refreshIfChanged();
    return repository.findAll().stream()
        .collect(Collectors.toMap(ConferenceOption::getId, Function.identity()));
  }

  private void synchronize(List<OptionDefinition> definitions) {
    transactions.executeWithoutResult(
        status -> {
          Map<String, ConferenceOption> existing =
              repository.findAll().stream()
                  .collect(Collectors.toMap(ConferenceOption::getId, Function.identity()));
          Set<String> listed = new HashSet<>();
          for (int i = 0; i < definitions.size(); i++) {
            OptionDefinition def = definitions.get(i);
            listed.add(def.id());
            ConferenceOption option = existing.get(def.id());
            if (option == null) {
              repository.save(
                  new ConferenceOption(def.id(), def.category(), def.name(), def.active(), i));
            } else {
              option.update(def.category(), def.name(), def.active(), i);
            }
          }
          existing.values().stream()
              .filter(o -> !listed.contains(o.getId()))
              .forEach(ConferenceOption::deactivate);
        });
  }
}
