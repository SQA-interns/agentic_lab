package si.konferenca.registration.adapter.in.web;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import si.konferenca.registration.domain.CaptchaVerifier.CaptchaUnavailableException;
import si.konferenca.registration.domain.StorageFailedException;
import si.konferenca.registration.domain.ValidationFailedException;

/** Maps every failure to a problem answer of the contract (specification section 7). */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ValidationFailedException.class)
  ResponseEntity<Map<String, Object>> rejected(ValidationFailedException e) {
    return Problems.validation(e.errors());
  }

  @ExceptionHandler(CaptchaUnavailableException.class)
  ResponseEntity<Map<String, Object>> captchaUnavailable(CaptchaUnavailableException e) {
    LOG.warn("anti-automation verification unavailable: {}", causeName(e));
    return Problems.problem(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @ExceptionHandler(StorageFailedException.class)
  ResponseEntity<Map<String, Object>> notStored(StorageFailedException e) {
    // The message holds the registration id only; the cause may hold values, so only its class.
    LOG.error("{}: {}", e.getMessage(), causeName(e));
    return Problems.problem(HttpStatus.SERVICE_UNAVAILABLE);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> other(Exception e) {
    if (e instanceof ErrorResponse response) {
      return Problems.problem(response.getStatusCode());
    }
    LOG.error("unexpected error: {}", e.getClass().getName());
    return Problems.problem(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private static String causeName(Throwable e) {
    return e.getCause() == null ? "no cause" : e.getCause().getClass().getName();
  }
}
