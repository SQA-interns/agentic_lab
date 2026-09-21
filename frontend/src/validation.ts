/**
 * Client-side validation (specification § 5.5).
 *
 * The rules come from the backend through `fieldRules`, so the browser cannot enforce a
 * different rule set than the server. This is a usability layer: the backend repeats
 * every rule for every request, and a participant who bypasses the page gains nothing
 * (AC-G-03).
 */
import type { ApiFieldError, FieldRule } from './api.js';

export interface FormValues {
  readonly fields: Readonly<Record<string, string>>;
  readonly consents: Readonly<Record<string, boolean>>;
}

/** Same trimming rule as the backend: surrounding whitespace is not significant. */
export function trimValue(value: string): string {
  return value.normalize('NFC').replace(/^[\s\uFEFF\u00A0]+|[\s\uFEFF\u00A0]+$/gu, '');
}

const EMAIL_PATTERN = /^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)+$/u;

const LABELS: Readonly<Record<string, string>> = {
  firstName: 'First name',
  lastName: 'Last name',
  email: 'Email',
  organization: 'Organization / institution',
  studyInstitution: 'Study institution',
  studyProgramme: 'Study programme',
  studentId: 'Student ID',
};

export function labelFor(field: string): string {
  return LABELS[field] ?? field;
}

export function validateField(field: string, rawValue: string, rule: FieldRule): string | null {
  const value = trimValue(rawValue);
  const label = labelFor(field);

  if (rule.required && value.length === 0) {
    return `${label} is required.`;
  }
  if (value.length === 0) {
    return null;
  }
  if (value.length > rule.maxLength) {
    return `${label} must be at most ${rule.maxLength} characters.`;
  }
  if (rule.minLength !== undefined && value.length < rule.minLength) {
    return `${label} must be at least ${rule.minLength} characters.`;
  }
  if (rule.format === 'email' && !EMAIL_PATTERN.test(value)) {
    return 'Enter a valid email address.';
  }
  if (rule.pattern !== undefined && !new RegExp(rule.pattern, 'u').test(value)) {
    return `${label} has an invalid format.`;
  }
  return null;
}

/**
 * Validate the whole form.
 *
 * Returns one message per offending control, keyed the same way the backend keys its
 * field errors, so both sources render through the same code path.
 */
export function validateForm(
  values: FormValues,
  fieldRules: Readonly<Record<string, FieldRule>>,
  requiredConsents: readonly string[],
): Record<string, string> {
  const errors: Record<string, string> = {};

  for (const [field, rule] of Object.entries(fieldRules)) {
    const message = validateField(field, values.fields[field] ?? '', rule);
    if (message !== null) {
      errors[field] = message;
    }
  }

  for (const consentId of requiredConsents) {
    if (values.consents[consentId] !== true) {
      errors[`consents.${consentId}`] =
        'You must agree to the processing of your personal data to register.';
    }
  }

  return errors;
}

/** Convert backend field errors into the same keyed shape the form renders. */
export function toErrorMap(fields: readonly ApiFieldError[]): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const entry of fields) {
    // Several option errors share the `selectedOptionIds` key; keep them all visible.
    errors[entry.field] = errors[entry.field]
      ? `${errors[entry.field]} ${entry.message}`
      : entry.message;
  }
  return errors;
}
