package si.konferenca.registration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.ConferenceOption;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.OptionDefinition;
import si.konferenca.registration.integration.OptionsFileReader;
import si.konferenca.registration.integration.OptionsFileReader.FileStamp;
import si.konferenca.registration.persistence.ConferenceOptionRepository;

class OptionCatalogServiceTest {

  private final OptionsFileReader reader = mock(OptionsFileReader.class);
  private final ConferenceOptionRepository repository = mock(ConferenceOptionRepository.class);
  private final List<ConferenceOption> stored = new ArrayList<>();
  private OptionCatalogService service;

  private static FileStamp stamp(long millis) {
    return new FileStamp(FileTime.fromMillis(millis), 10);
  }

  @BeforeEach
  void setUp() throws IOException {
    service =
        new OptionCatalogService(
            reader, repository, new TransactionTemplate(mock(PlatformTransactionManager.class)));
    when(reader.file()).thenReturn(Path.of("options.json"));
    when(repository.findAll()).thenAnswer(inv -> new ArrayList<>(stored));
    when(repository.save(any()))
        .thenAnswer(
            inv -> {
              stored.add(inv.getArgument(0));
              return inv.getArgument(0);
            });
    when(repository.findByActiveTrue())
        .thenAnswer(inv -> stored.stream().filter(ConferenceOption::isActive).toList());
  }

  @Test
  void initializeInsertsUpdatesAndDeactivatesUnlistedOptions() throws IOException {
    stored.add(new ConferenceOption("keep", OptionCategory.MEAL, "Old name", false, 5));
    stored.add(new ConferenceOption("gone", OptionCategory.EVENT, "Gone", true, 0));
    when(reader.stamp()).thenReturn(stamp(1));
    when(reader.read())
        .thenReturn(
            List.of(
                new OptionDefinition("new", OptionCategory.WORKSHOP, "New", true),
                new OptionDefinition("keep", OptionCategory.MEAL, "New name", true)));

    service.initialize();

    assertThat(stored)
        .extracting(ConferenceOption::getId, ConferenceOption::getName, ConferenceOption::isActive)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("keep", "New name", true),
            org.assertj.core.groups.Tuple.tuple("gone", "Gone", false),
            org.assertj.core.groups.Tuple.tuple("new", "New", true));
    assertThat(service.activeOptions())
        .extracting(ConferenceOption::getId)
        .containsExactly("new", "keep");
  }

  @Test
  void initializeFailsOnAnInvalidFile() throws IOException {
    when(reader.stamp()).thenReturn(stamp(1));
    when(reader.read()).thenThrow(new OptionsFileReader.InvalidOptionsFileException("bad"));

    assertThatThrownBy(service::initialize)
        .isInstanceOf(OptionsFileReader.InvalidOptionsFileException.class);
  }

  @Test
  void unchangedFileIsNotReread() throws IOException {
    when(reader.stamp()).thenReturn(stamp(1));
    when(reader.read()).thenReturn(List.of());
    service.initialize();

    service.activeOptions();
    service.catalog();

    verify(reader, times(1)).read();
  }

  @Test
  void invalidChangeKeepsThePreviousCatalogAndIsNotRetriedUntilTheNextChange() throws IOException {
    when(reader.stamp()).thenReturn(stamp(1));
    when(reader.read())
        .thenReturn(List.of(new OptionDefinition("a", OptionCategory.OTHER, "A", true)));
    service.initialize();
    when(reader.stamp()).thenReturn(stamp(2));
    when(reader.read()).thenThrow(new OptionsFileReader.InvalidOptionsFileException("bad"));

    assertThat(service.activeOptions()).extracting(ConferenceOption::getId).containsExactly("a");
    service.activeOptions();

    verify(reader, times(2)).read();
  }

  @Test
  void unreadableFileAtRuntimeKeepsThePreviousCatalog() throws IOException {
    when(reader.stamp()).thenReturn(stamp(1));
    when(reader.read())
        .thenReturn(List.of(new OptionDefinition("a", OptionCategory.OTHER, "A", true)));
    service.initialize();
    when(reader.stamp()).thenThrow(new IOException("deleted"));

    assertThat(service.catalog()).containsOnlyKeys("a");
    verify(repository, never()).delete(any());
  }
}
