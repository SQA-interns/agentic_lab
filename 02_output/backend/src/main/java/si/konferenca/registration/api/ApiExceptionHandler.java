package si.konferenca.registration.api;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import si.konferenca.registration.application.StorageException;
import si.konferenca.registration.domain.ErrorCode;
import si.konferenca.registration.domain.ValidationError;

/** Maps failures to problem bodies without internal details (SB-07, ES-07). */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler({StorageException.class, DataAccessException.class, TransactionException.class})
  ResponseEntity<Map<String, Object>> storage(RuntimeException e) {
    LOG.error("Registration not stored: {}", causeChain(e));
    return Problems.of(HttpStatus.SERVICE_UNAVAILABLE);
  }

  /**
   * Exception class names only: messages from the database or parser can contain submitted personal
   * data (ES-07).
   */
  static String causeChain(Throwable e) {
    StringBuilder chain = new StringBuilder();
    for (Throwable t = e; t != null && chain.length() < 500; t = t.getCause()) {
      if (!chain.isEmpty()) {
        chain.append(" <- ");
      }
      chain.append(t.getClass().getName());
    }
    return chain.toString();
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
    return Problems.validation(
        HttpStatus.BAD_REQUEST, List.of(ValidationError.of("type", ErrorCode.MALFORMED)));
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<Map<String, Object>> mediaType(HttpMediaTypeNotSupportedException e) {
    return Problems.of(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException e) {
    return Problems.of(HttpStatus.METHOD_NOT_ALLOWED);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<Map<String, Object>> notFound(NoResourceFoundException e) {
    return Problems.of(HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Map<String, Object>> unexpected(Exception e) {
    LOG.error("Unexpected error: {}", causeChain(e));
    return Problems.of(HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
