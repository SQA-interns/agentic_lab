package lab.conference.platform;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Operator tool: reads one password line from stdin and prints the {bcrypt} hash for
 * ORGANIZER_PASSWORD_HASH (SB-03). The password is never taken from arguments, so it does not
 * appear in shell history or process lists. Run it with tools/hash-password.sh.
 */
public final class PasswordHashCli {

  static final int COST = 12;

  private PasswordHashCli() {}

  public static void main(String[] args) throws IOException {
    System.exit(run(System.in, System.out, System.err));
  }

  static int run(InputStream in, PrintStream out, PrintStream err) throws IOException {
    String password =
        new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).readLine();
    if (password == null || password.length() < 12) {
      err.println("Provide a password of at least 12 characters on stdin.");
      return 1;
    }
    out.println("{bcrypt}" + new BCryptPasswordEncoder(COST).encode(password));
    return 0;
  }
}
