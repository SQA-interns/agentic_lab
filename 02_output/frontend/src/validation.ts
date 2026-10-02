// Checks done before anything is sent (NFR-03). The backend repeats them and stays the authority.
import type { FieldName } from "./api";
import type { FieldSpec } from "./texts";

// One address: a local part, one @ and a domain of at least two labels; no whitespace.
const EMAIL = /^[^\s@]+@[^\s@.]+(\.[^\s@.]+)+$/;

export type ErrorCodes = Partial<Record<FieldName | "consent" | "captchaToken", string>>;

export interface FormValues {
  texts: Partial<Record<FieldName, string>>;
  consent: boolean;
  captchaToken: string;
}

/** The error code of each field that fails a check; empty when the form may be sent. */
export function validate(fields: FieldSpec[], values: FormValues): ErrorCodes {
  const errors: ErrorCodes = {};
  for (const field of fields) {
    const value = (values.texts[field.name] ?? "").trim();
    if (value === "") {
      errors[field.name] = "required";
    } else if ([...value].length > field.maxLength) {
      errors[field.name] = "too_long";
    } else if (field.control === "email" && !EMAIL.test(value)) {
      errors[field.name] = "invalid_format";
    }
  }
  if (!values.consent) {
    errors.consent = "consent_required";
  }
  if (values.captchaToken === "") {
    errors.captchaToken = "captcha_failed";
  }
  return errors;
}
