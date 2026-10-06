// Checks before sending (NFR-03); the backend repeats every rule (SB-01).
import type { FormConsent, RegistrationType } from "./api";

export const MESSAGES = {
  required: "This field is required.",
  email: "Enter a valid email address.",
  consent: "This consent is required.",
  captcha: "Please confirm that you are not a robot.",
} as const;

/** Same pattern as the backend (docs/02_specification.md §4). */
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export const FIELDS: Record<RegistrationType, readonly string[]> = {
  external: ["firstName", "lastName", "email", "organization"],
  student: [
    "firstName",
    "lastName",
    "email",
    "studyInstitution",
    "studyProgramme",
    "studentId",
  ],
};

export const LABELS: Record<string, string> = {
  firstName: "First name",
  lastName: "Last name",
  email: "Email",
  organization: "Organization / institution",
  studyInstitution: "Study institution",
  studyProgramme: "Study programme",
  studentId: "Student ID",
};

export function isValidEmail(value: string): boolean {
  return EMAIL.test(value.trim());
}

/** Errors by field name; consents use "consents.<id>", the captcha "captchaToken". */
export function validate(
  type: RegistrationType,
  values: Record<string, string>,
  consents: readonly FormConsent[],
  given: ReadonlySet<string>,
  captchaToken: string,
): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const field of FIELDS[type]) {
    const value = (values[field] ?? "").trim();
    if (value === "") {
      errors[field] = MESSAGES.required;
    } else if (field === "email" && !isValidEmail(value)) {
      errors[field] = MESSAGES.email;
    }
  }
  for (const consent of consents) {
    if (consent.mandatory && !given.has(consent.id)) {
      errors[`consents.${consent.id}`] = MESSAGES.consent;
    }
  }
  if (captchaToken === "") {
    errors.captchaToken = MESSAGES.captcha;
  }
  return errors;
}
