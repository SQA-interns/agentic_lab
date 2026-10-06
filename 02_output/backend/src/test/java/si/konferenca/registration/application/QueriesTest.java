package si.konferenca.registration.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import si.konferenca.registration.domain.Registration;

class QueriesTest {

  private final CaptchaVerifier captcha = mock(CaptchaVerifier.class);

  @Test
  void setupHidesSiteKeyInTestModeAndListsActiveOptionsOnly() {
    when(captcha.testMode()).thenReturn(true);
    when(captcha.siteKey()).thenReturn("should-not-leak");

    RegistrationSetup s = new SetupQuery("Konf", Fixtures.CATALOG, captcha).setup();

    assertThat(s.captchaTestMode()).isTrue();
    assertThat(s.captchaSiteKey()).isEmpty();
    assertThat(s.options()).containsExactly(Fixtures.WORKSHOP, Fixtures.GALA);
    assertThat(s.conferenceName()).isEqualTo("Konf");
    assertThat(s.consent()).isEqualTo(Fixtures.CONSENT);
  }

  @Test
  void setupGivesSiteKeyInProductionMode() {
    when(captcha.siteKey()).thenReturn("site");

    assertThat(new SetupQuery("K", Fixtures.CATALOG, captcha).setup().captchaSiteKey())
        .isEqualTo("site");
  }

  @Test
  void exportWritesAllStoredRegistrations() {
    RegistrationStore store = mock(RegistrationStore.class);
    WorkbookWriter writer = mock(WorkbookWriter.class);
    List<Registration> all = List.of(Fixtures.registration());
    when(store.findAll()).thenReturn(all);
    when(writer.write(all)).thenReturn(new byte[] {1, 2});

    byte[] out =
        new ExportService(
                store,
                writer,
                new TransactionTemplate(new RegistrationServiceTest.FakeTransactions()))
            .export();

    assertThat(out).containsExactly(1, 2);
  }
}
