package lab.conference.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class PasswordHashCliTest {

  private static int run(String input, ByteArrayOutputStream out, ByteArrayOutputStream err)
      throws Exception {
    return PasswordHashCli.run(
        new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
        new PrintStream(out, true, StandardCharsets.UTF_8),
        new PrintStream(err, true, StandardCharsets.UTF_8));
  }

  @Test
  void printsAHashTheApplicationAccepts() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    assertThat(run("correct horse battery\n", out, new ByteArrayOutputStream())).isZero();
    String hash = out.toString(StandardCharsets.UTF_8).trim();
    assertThat(hash).startsWith("{bcrypt}$2a$12$").doesNotContain("correct");
    SecurityConfig.requireSlowHash(hash);
    assertThat(
            PasswordEncoderFactories.createDelegatingPasswordEncoder()
                .matches("correct horse battery", hash))
        .isTrue();
  }

  @Test
  void rejectsMissingOrShortPasswords() throws Exception {
    ByteArrayOutputStream err = new ByteArrayOutputStream();
    assertThat(run("", new ByteArrayOutputStream(), err)).isEqualTo(1);
    assertThat(run("short\n", new ByteArrayOutputStream(), err)).isEqualTo(1);
    assertThat(err.toString(StandardCharsets.UTF_8)).contains("at least 12");
  }
}
