package si.konferenca.registration.api;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import si.konferenca.registration.api.dto.FormConfigResponse;
import si.konferenca.registration.service.FormConfigService;

/** Public form configuration: active options, consents and captcha mode. */
@RestController
public class FormConfigController {

  private final FormConfigService formConfigService;

  public FormConfigController(FormConfigService formConfigService) {
    this.formConfigService = formConfigService;
  }

  @GetMapping(path = "/api/form-config", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<FormConfigResponse> formConfig() {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(FormConfigResponse.from(formConfigService.formConfig()));
  }
}
