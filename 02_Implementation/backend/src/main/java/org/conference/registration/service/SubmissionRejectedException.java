package org.conference.registration.service;

/**
 * An anti-automation check failed. The reason is only logged; clients get a generic message so bots
 * learn nothing about which check they tripped.
 */
public class SubmissionRejectedException extends RuntimeException {
  private static final long serialVersionUID = 1L;

  public SubmissionRejectedException(String reason) {
    super(reason);
  }
}
