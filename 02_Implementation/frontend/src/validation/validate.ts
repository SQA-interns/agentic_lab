// Client-side validation mirroring specification §6. For usability only — the
// backend is the security boundary.

import type { RegistrationType } from "../api/client";

export type FieldName =
  | "firstName"
  | "lastName"
  | "email"
  | "organization"
  | "studyInstitution"
  | "studyProgramme"
  | "studentId"
  | "personalDataConsent"
  | "recaptchaToken";

export type FieldErrors = Partial<Record<FieldName, string>>;

export interface FormValues {
  type: RegistrationType;
  firstName: string;
  lastName: string;
  email: string;
  organization: string;
  studyInstitution: string;
  studyProgramme: string;
  studentId: string;
  personalDataConsent: boolean;
  recaptchaToken: string;
}

export const MESSAGES = {
  REQUIRED: "This field is required.",
  INVALID_EMAIL: "Enter a valid email address.",
  TOO_LONG: "This value is too long.",
  INVALID_CHARACTERS: "This value contains characters that are not allowed.",
  CONSENT_REQUIRED: "You must agree to the processing of your personal data.",
  RECAPTCHA_FAILED: "Please confirm you are not a robot.",
} as const;

export const FIELD_LIMITS: Record<
  Exclude<FieldName, "personalDataConsent" | "recaptchaToken">,
  number
> = {
  firstName: 100,
  lastName: 100,
  email: 254,
  organization: 200,
  studyInstitution: 200,
  studyProgramme: 200,
  studentId: 50,
};

export function fieldsFor(
  type: RegistrationType,
): (keyof typeof FIELD_LIMITS)[] {
  const common: (keyof typeof FIELD_LIMITS)[] = [
    "firstName",
    "lastName",
    "email",
  ];
  return type === "EXTERNAL"
    ? [...common, "organization"]
    : [...common, "studyInstitution", "studyProgramme", "studentId"];
}

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/u;
// Unicode "Cc" (control) and "Cf" (format) characters are rejected by the backend.
const DISALLOWED = /[\p{Cc}\p{Cf}]/u;

export function isValidEmail(value: string): boolean {
  if (!EMAIL.test(value)) return false;
  const domain = value.slice(value.indexOf("@") + 1);
  return (
    !domain.startsWith(".") && !domain.endsWith(".") && !domain.includes("..")
  );
}

export function validate(values: FormValues): FieldErrors {
  const errors: FieldErrors = {};
  for (const field of fieldsFor(values.type)) {
    const value = values[field].trim();
    if (value === "") {
      errors[field] = MESSAGES.REQUIRED;
    } else if ([...value].length > FIELD_LIMITS[field]) {
      errors[field] = MESSAGES.TOO_LONG;
    } else if (DISALLOWED.test(value)) {
      errors[field] = MESSAGES.INVALID_CHARACTERS;
    } else if (field === "email" && !isValidEmail(value)) {
      errors[field] = MESSAGES.INVALID_EMAIL;
    }
  }
  if (!values.personalDataConsent) {
    errors.personalDataConsent = MESSAGES.CONSENT_REQUIRED;
  }
  if (values.recaptchaToken === "") {
    errors.recaptchaToken = MESSAGES.RECAPTCHA_FAILED;
  }
  return errors;
}

/** Message for a server-side field error code (specification §12.1). */
export function messageForCode(code: string, serverMessage: string): string {
  return (MESSAGES as Record<string, string>)[code] ?? serverMessage;
}
