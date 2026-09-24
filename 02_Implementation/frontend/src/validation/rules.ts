import type { FieldDef, FieldKind, FieldName } from '../types';

/*
 * Mirror of the backend rules (backend ValidationRules.java, specification §5). Frontend validation
 * is for usability only; the backend validates every request independently.
 */

const PATTERNS: Record<FieldKind, RegExp> = {
  name: /^[\p{L}\p{M}][\p{L}\p{M} .'’-]*$/u,
  email: /^[^@\s\p{Cc}]+@[^@\s\p{Cc}]+\.[^@\s\p{Cc}]{2,}$/u,
  text: /^[^\p{Cc}]*$/u,
  studentId: /^[\p{L}\p{N}/-]+$/u,
};

const MAX_LENGTH: Record<FieldKind, number> = {
  name: 100,
  email: 254,
  text: 200,
  studentId: 50,
};

export const MESSAGES = {
  required: 'This field is required.',
  name: 'Please use letters, spaces, apostrophes or hyphens only.',
  email: 'Please enter a valid email address.',
  text: 'This field contains invalid characters.',
  studentId: "Please use letters, digits, '/' or '-' only.",
  consent: 'You must accept the privacy statement.',
  tooLong: (max: number) => `This value is too long (maximum ${String(max)} characters).`,
} as const;

/** Leading/trailing whitespace is not significant (FORM_SCHEMA §General field rules). */
export function normalize(value: string): string {
  return value.trim().normalize('NFC');
}

export function validateField(kind: FieldKind, raw: string): string | undefined {
  const value = normalize(raw);
  if (value === '') {
    return MESSAGES.required;
  }
  const max = MAX_LENGTH[kind];
  if (value.length > max) {
    return MESSAGES.tooLong(max);
  }
  if (!PATTERNS[kind].test(value)) {
    return MESSAGES[kind];
  }
  return undefined;
}

export type FieldErrors = Partial<Record<FieldName | 'privacyConsent' | 'optionIds', string>>;

export function validateForm(
  fields: readonly FieldDef[],
  values: Partial<Record<FieldName, string>>,
  privacyConsent: boolean,
): FieldErrors {
  const errors: FieldErrors = {};
  for (const field of fields) {
    const message = validateField(field.kind, values[field.name] ?? '');
    if (message !== undefined) {
      errors[field.name] = message;
    }
  }
  if (!privacyConsent) {
    errors.privacyConsent = MESSAGES.consent;
  }
  return errors;
}
