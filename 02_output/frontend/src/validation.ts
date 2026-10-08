// Browser-side checks that mirror the backend rules (specification section 4, NFR-03).
// The backend stays authoritative (SB-01).

import type { Category, ConferenceOption, FormConfig, RegistrationType } from "./api";

export type Values = Record<string, string>;
export type Errors = Record<string, string>;

/** String.prototype.trim removes Unicode whitespace including U+00A0 and U+FEFF (KP-03). */
export const clean = (value: string | undefined): string => (value ?? "").trim();

export const FIELDS: Record<RegistrationType, { name: string; max: number }[]> = {
  EXTERNAL: [
    { name: "firstName", max: 100 },
    { name: "lastName", max: 100 },
    { name: "email", max: 254 },
    { name: "organization", max: 200 },
  ],
  STUDENT: [
    { name: "firstName", max: 100 },
    { name: "lastName", max: 100 },
    { name: "email", max: 254 },
    { name: "studyInstitution", max: 200 },
    { name: "studyProgramme", max: 200 },
    { name: "studentId", max: 50 },
  ],
};

export const MESSAGES: Record<string, string> = {
  required: "This field is required.",
  too_long: "This value is too long.",
  invalid_email: "Enter a valid email address.",
  invalid_characters: "This value contains characters that are not allowed.",
  not_allowed_for_type: "This field does not belong to the selected form.",
  unknown_option: "A selected option is not offered.",
  inactive_option: "A selected option is no longer offered.",
  option_not_available: "A selected option is not available for this registration type.",
  too_many_options: "Too many options are selected in one category.",
  consent_missing: "Please give the required consent.",
  unknown_consent: "A consent is not recognised.",
  captcha_failed: "Please confirm that you are not a robot.",
  malformed: "The registration could not be read.",
};

const LOCAL = /^[^\s",;<>@]{1,64}$/u;
const LABEL = /^[\p{L}\p{N}](?:[\p{L}\p{N}-]*[\p{L}\p{N}])?$/u;
const TOP_LEVEL = /^\p{L}{2,}$/u;
// Control (Cc) and format (Cf) characters.
const CONTROL = /[\p{Cc}\p{Cf}]/u;

export function isValidEmail(email: string): boolean {
  if (email.length > 254 || CONTROL.test(email)) {
    return false;
  }
  const parts = email.split("@");
  if (parts.length !== 2 || !LOCAL.test(parts[0])) {
    return false;
  }
  const labels = parts[1].split(".");
  return (
    labels.length >= 2 &&
    labels.every((label) => label.length <= 63 && LABEL.test(label)) &&
    TOP_LEVEL.test(labels[labels.length - 1])
  );
}

export function validate(
  type: RegistrationType,
  values: Values,
  optionIds: string[],
  consentIds: string[],
  token: string,
  config: FormConfig,
): Errors {
  const errors: Errors = {};
  for (const { name, max } of FIELDS[type]) {
    const value = clean(values[name]);
    if (value === "") {
      errors[name] = MESSAGES.required;
    } else if ([...value].length > max) {
      errors[name] = MESSAGES.too_long;
    } else if (CONTROL.test(value)) {
      errors[name] = MESSAGES.invalid_characters;
    } else if (name === "email" && !isValidEmail(value)) {
      errors[name] = MESSAGES.invalid_email;
    }
  }
  const counts = new Map<Category, number>();
  for (const id of optionIds) {
    const option = config.options.find((o: ConferenceOption) => o.id === id);
    if (option) {
      counts.set(option.category, (counts.get(option.category) ?? 0) + 1);
    }
  }
  for (const { category, maxSelections } of config.categories) {
    if ((counts.get(category) ?? 0) > maxSelections) {
      errors.optionIds = MESSAGES.too_many_options;
    }
  }
  if (config.consents.some((c) => c.mandatory && !consentIds.includes(c.id))) {
    errors.consentIds = MESSAGES.consent_missing;
  }
  if (token === "") {
    errors.recaptchaToken = MESSAGES.captcha_failed;
  }
  return errors;
}
