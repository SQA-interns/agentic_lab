import { FIELDS, type FieldValues } from "./fields";
import type { Consent, FieldErrors, RegistrationType } from "./types";

// Client-side checks before sending (NFR-03). The backend repeats every check (SB-01).

export const MESSAGES = {
  required: "This field is required.",
  email: "Enter a valid email address.",
  consent: "This consent is required.",
  recaptcha: "Confirm that you are not a robot.",
} as const;

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function isValidEmail(value: string): boolean {
  return EMAIL.test(value.trim());
}

export function validate(
  type: RegistrationType,
  values: FieldValues,
  consents: Consent[],
  givenConsents: string[],
): FieldErrors {
  const errors: FieldErrors = {};
  for (const field of FIELDS[type]) {
    const value = values[field.name].trim();
    if (value === "") {
      errors[field.name] = MESSAGES.required;
    } else if (field.name === "email" && !isValidEmail(value)) {
      errors[field.name] = MESSAGES.email;
    }
  }
  if (consents.some((c) => c.required && !givenConsents.includes(c.id))) {
    errors.consents = MESSAGES.consent;
  }
  return errors;
}

/** The request body: only the fields of the selected type, trimmed. */
export function toRequestBody(
  type: RegistrationType,
  values: FieldValues,
  optionIds: string[],
  consents: string[],
  recaptchaToken: string,
): Record<string, unknown> {
  const body: Record<string, unknown> = { type };
  for (const field of FIELDS[type]) {
    body[field.name] = values[field.name].trim();
  }
  body.optionIds = optionIds;
  body.consents = consents;
  body.recaptchaToken = recaptchaToken;
  return body;
}
