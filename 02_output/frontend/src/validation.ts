import { messages } from "./messages";
import type { CategoryOptions, Consent, RegistrationType, TextFieldName } from "./types";

// The field rules of the specification, section 4, checked before submission (NFR-03). The
// backend checks them again (SB-01).

export const MAX_LENGTH: Record<TextFieldName, number> = {
  firstName: 100,
  lastName: 100,
  email: 254,
  organization: 200,
  studyInstitution: 200,
  studyProgramme: 200,
  studentId: 50,
};

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CONTROL = /\p{Cc}/u;

export function fieldsFor(type: RegistrationType): TextFieldName[] {
  return type === "EXTERNAL"
    ? ["firstName", "lastName", "email", "organization"]
    : ["firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId"];
}

/** The error message for one text field, or null when the value is valid. */
export function textFieldError(field: TextFieldName, raw: string): string | null {
  const label = messages.fields[field];
  const value = raw.trim();
  if (value === "") {
    return messages.required(label);
  }
  if (CONTROL.test(value)) {
    return messages.invalidCharacters(label);
  }
  if ([...value].length > MAX_LENGTH[field]) {
    return messages.tooLong(label, MAX_LENGTH[field]);
  }
  if (field === "email" && !EMAIL.test(value)) {
    return messages.invalidEmail;
  }
  return null;
}

export interface Draft {
  type: RegistrationType;
  values: Partial<Record<TextFieldName, string>>;
  optionIds: string[];
  consentIds: string[];
  captchaToken: string;
}

/** Errors by request field name (text fields, optionIds, consentIds, captchaToken). */
export function validateDraft(
  draft: Draft,
  categories: CategoryOptions[],
  consents: Consent[],
): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const field of fieldsFor(draft.type)) {
    const error = textFieldError(field, draft.values[field] ?? "");
    if (error !== null) {
      errors[field] = error;
    }
  }
  for (const category of categories) {
    const selected = category.options.filter((option) =>
      draft.optionIds.includes(option.id),
    ).length;
    if (selected > category.maxSelections) {
      errors["optionIds"] = messages.tooManyOptions(category.maxSelections, category.category);
    }
  }
  if (consents.some((consent) => consent.mandatory && !draft.consentIds.includes(consent.id))) {
    errors["consentIds"] = messages.consentRequired;
  }
  if (draft.captchaToken === "") {
    errors["captchaToken"] = messages.captchaRequired;
  }
  return errors;
}
