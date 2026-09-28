import type { Consent } from "./api";
import type { FieldDefinition } from "./fields";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// eslint-disable-next-line no-control-regex
const CONTROL_CHARS = /[\u0000-\u001f\u007f]/;

export type Errors = Record<string, string>;

/**
 * Client-side validation for usability only; the backend is the security boundary.
 * Mirrors the backend rules: required after trimming, max length, email format,
 * no control characters and mandatory consents.
 */
export function validate(
  fields: FieldDefinition[],
  values: Record<string, string>,
  consents: Consent[],
  givenConsents: Record<string, boolean>,
): Errors {
  const errors: Errors = {};
  for (const field of fields) {
    const value = (values[field.name] ?? "").trim();
    if (value === "") {
      errors[field.name] = "This field is required.";
    } else if (value.length > field.maxLength) {
      errors[field.name] = "This value is too long.";
    } else if (CONTROL_CHARS.test(value)) {
      errors[field.name] = "This value contains invalid characters.";
    } else if (field.type === "email" && !EMAIL_PATTERN.test(value)) {
      errors[field.name] = "Email must be a valid email address.";
    }
  }
  for (const consent of consents) {
    if (consent.mandatory && !givenConsents[consent.id]) {
      errors[`consents.${consent.id}`] = "This consent is required.";
    }
  }
  return errors;
}
