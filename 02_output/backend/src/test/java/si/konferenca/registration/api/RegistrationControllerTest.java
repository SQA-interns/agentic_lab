package si.konferenca.registration.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import si.konferenca.registration.domain.FieldError;
import si.konferenca.registration.service.DuplicateEmailException;
import si.konferenca.registration.service.NotificationServiceTest;
import si.konferenca.registration.service.RegistrationRejectedException;
import si.konferenca.registration.service.RegistrationService;
import si.konferenca.registration.service.StorageFailureException;

class RegistrationControllerTest {

  private final RegistrationService service = mock(RegistrationService.class);
  private final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new RegistrationController(service))
          .setControllerAdvice(new ProblemHandler())
          .build();

  private static final String VALID =
      "{\"type\":\"EXTERNAL\",\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"a@b.si\","
          + "\"organization\":\"O\",\"optionIds\":[],\"consentIds\":[\"d\"],\"recaptchaToken\":\"t\"}";

  @Test
  void acceptedReturns201WithIdAndTime() throws Exception {
    when(service.register(any(), any())).thenReturn(NotificationServiceTest.student());
    mvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value("3f2b8c1e-7a4d-4e8b-9c61-2d5f0a9e4b17"))
        .andExpect(jsonPath("$.receivedAt").value("2026-10-08T10:00:00Z"));
  }

  @Test
  @DisplayName("SB-01 unknown properties and wrong JSON shapes are malformed")
  void strictParsing() throws Exception {
    for (String body :
        List.of(
            VALID.replace("}", ",\"admin\":true}"),
            "[]",
            "null",
            "{\"type\":\"EXTERNAL\",\"optionIds\":\"ws\"}",
            "{\"type\":{}}",
            "not json")) {
      mvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.errors[0].code").value("malformed"));
    }
    verify(service, never()).register(any(), any());
  }

  @Test
  void validationErrorsAreListed() throws Exception {
    when(service.register(any(), any()))
        .thenThrow(
            new RegistrationRejectedException(List.of(new FieldError("email", "invalid_email"))));
    mvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("urn:problem:invalid-registration"))
        .andExpect(jsonPath("$.errors[0].field").value("email"))
        .andExpect(jsonPath("$.errors[0].code").value("invalid_email"));
  }

  @Test
  void duplicateIs409() throws Exception {
    when(service.register(any(), any())).thenThrow(new DuplicateEmailException());
    mvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(VALID))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("This email address is already registered."));
  }

  @Test
  @DisplayName("SB-07 storage and unexpected failures are 500 without internal details")
  void failuresHideInternals() throws Exception {
    when(service.register(any(), any()))
        .thenThrow(new StorageFailureException(new IllegalStateException("/data/secret path")))
        .thenThrow(new IllegalStateException("SELECT * FROM registration"));
    for (int i = 0; i < 2; i++) {
      mvc.perform(post("/api/registrations").contentType(MediaType.APPLICATION_JSON).content(VALID))
          .andExpect(status().isInternalServerError())
          .andExpect(jsonPath("$.title").value("Registration could not be processed"))
          .andExpect(jsonPath("$.detail").doesNotExist())
          .andExpect(
              content()
                  .string(
                      org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))))
          .andExpect(
              content()
                  .string(
                      org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT"))));
    }
  }

  @Test
  void wrongContentTypeIs415Problem() throws Exception {
    mvc.perform(post("/api/registrations").contentType(MediaType.TEXT_PLAIN).content(VALID))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  }
}
