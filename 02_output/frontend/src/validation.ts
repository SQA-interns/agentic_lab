// Client-side checks mirroring docs/02_specification.md section 5 (NFR-03). The backend is
// authoritative (SB-01); these only give earlier feedback.
import type { FieldError, FormDefinition } from "./api";

// Same as the backend: anything without whitespace, "@", anything, ".", anything.
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/u;

/** String.prototype.trim removes Unicode White_Space and U+FEFF, including U+00A0 (KP-03). */
export function trimValue(value: string): string {
  return value.trim();
}

export interface Draft {
  values: Record<string, string>;
  optionIds: string[];
  consentIds: string[];
  recaptchaToken: string;
}

export function validate(form: FormDefinition, draft: Draft): FieldError[] {
  const errors: FieldError[] = [];
  for (const field of form.fields) {
    const value = trimValue(draft.values[field.name] ?? "");
    if (value === "") {
      errors.push({ field: field.name, code: "REQUIRED" });
    } else if (value.length > field.maxLength) {
      errors.push({ field: field.name, code: "TOO_LONG" });
    } else if (field.name === "email" && !EMAIL.test(value)) {
      errors.push({ field: field.name, code: "INVALID_EMAIL" });
    }
  }
  for (const category of form.categories) {
    const ids = new Set(category.options.map((o) => o.id));
    const count = draft.optionIds.filter((id) => ids.has(id)).length;
    if (count > category.maxSelections) {
      errors.push({ field: "optionIds", code: "TOO_MANY_OPTIONS" });
      break;
    }
  }
  for (const consent of form.consents) {
    if (consent.mandatory && !draft.consentIds.includes(consent.id)) {
      errors.push({
        field: "consentIds",
        code: "CONSENT_REQUIRED",
        consentId: consent.id,
      });
    }
  }
  if (draft.recaptchaToken === "") {
    errors.push({ field: "recaptchaToken", code: "CAPTCHA_FAILED" });
  }
  return errors;
}
