package org.conference.registration.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.conference.registration.service.RateLimiter.Bucket;
import org.junit.jupiter.api.Test;

class RateLimitFilterTest {

  @Test
  void classifiesRequestsIntoBuckets() {
    assertThat(RateLimitFilter.bucketFor("POST", "/api/registrations/external"))
        .isEqualTo(Bucket.REGISTRATION);
    assertThat(RateLimitFilter.bucketFor("POST", "/api/registrations/student"))
        .isEqualTo(Bucket.REGISTRATION);
    assertThat(RateLimitFilter.bucketFor("GET", "/api/form-token")).isEqualTo(Bucket.FORM_TOKEN);
    assertThat(RateLimitFilter.bucketFor("GET", "/api/admin/registrations/export"))
        .isEqualTo(Bucket.ADMIN);
    assertThat(RateLimitFilter.bucketFor("GET", "/api/options")).isNull();
    assertThat(RateLimitFilter.bucketFor("GET", "/actuator/health")).isNull();
  }
}
