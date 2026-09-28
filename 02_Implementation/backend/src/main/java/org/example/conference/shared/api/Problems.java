package org.example.conference.shared.api;

import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Builds privacy-safe problem details (no submitted values, no stack traces). */
public final class Problems {

  private Problems() {}

  public static ProblemDetail of(HttpStatusCode status, ErrorCode code, List<FieldError> errors) {
    ProblemDetail problem = ProblemDetail.forStatus(status);
    problem.setTitle(code.name());
    problem.setProperty("code", code.name());
    if (!errors.isEmpty()) {
      problem.setProperty("errors", errors);
    }
    return problem;
  }
}
