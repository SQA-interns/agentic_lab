import { FIELDS_BY_TYPE } from "./fields";
import type {
  ConsentDefinition,
  FieldErrors,
  FormValues,
  RegistrationType,
} from "./types";

// Mirrors the backend rules for usability only; the backend is the security boundary.
export const EMAIL_PATTERN =
  /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$/;

const CONTROL_CHARS = /\p{Cc}/u;

export const MESSAGES = {
  required: "This field is required.",
  invalidEmail: "Enter a valid email address.",
  invalidChars: "Contains characters that are not allowed.",
  tooLong: (max: number) => `Must be at most ${max} characters.`,
  consentRequired: "This consent is required.",
  captchaRequired: "Please confirm that you are not a robot.",
};

export function validateForm(
  type: RegistrationType,
  values: FormValues,
  consents: ConsentDefinition[],
  givenConsentIds: ReadonlySet<string>,
): FieldErrors {
  const errors: FieldErrors = {};
  for (const field of FIELDS_BY_TYPE[type]) {
    const value = values[field.name].trim();
    if (value === "") {
      errors[field.name] = MESSAGES.required;
    } else if (value.length > field.maxLength) {
      errors[field.name] = MESSAGES.tooLong(field.maxLength);
    } else if (CONTROL_CHARS.test(value)) {
      errors[field.name] = MESSAGES.invalidChars;
    } else if (field.inputType === "email" && !EMAIL_PATTERN.test(value)) {
      errors[field.name] = MESSAGES.invalidEmail;
    }
  }
  for (const consent of consents) {
    if (consent.required && !givenConsentIds.has(consent.id)) {
      errors[`consents.${consent.id}`] = MESSAGES.consentRequired;
    }
  }
  return errors;
}
