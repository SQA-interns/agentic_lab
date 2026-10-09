package si.konferenca.registration.acceptance.support;

import java.lang.reflect.Method;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayNameGenerator;

/** Shows a test method {@code ac_001_02_someBehaviour} as {@code AC-001-02 someBehaviour}. */
public class AcceptanceCriterionNames extends DisplayNameGenerator.Standard {

  private static final Pattern AC_METHOD = Pattern.compile("^ac_(\\d{3})_(\\d{2})_(.*)$");

  @Override
  public String generateDisplayNameForMethod(
      List<Class<?>> enclosingInstanceTypes, Class<?> testClass, Method testMethod) {
    Matcher matcher = AC_METHOD.matcher(testMethod.getName());
    if (matcher.matches()) {
      return "AC-" + matcher.group(1) + "-" + matcher.group(2) + " " + matcher.group(3);
    }
    return super.generateDisplayNameForMethod(enclosingInstanceTypes, testClass, testMethod);
  }
}
