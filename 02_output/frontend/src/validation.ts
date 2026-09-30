import type { FormType } from './api';

// Client-side checks mirror the backend rules for usability only (BR-02); the backend decides.

export const TEXT_FIELDS: Record<
  FormType,
  { name: string; label: string; max: number; type?: string }[]
> = {
  external: [
    { name: 'firstName', label: 'First name', max: 100 },
    { name: 'lastName', label: 'Last name', max: 100 },
    { name: 'email', label: 'Email', max: 254, type: 'email' },
    { name: 'organization', label: 'Organization / institution', max: 200 },
  ],
  student: [
    { name: 'firstName', label: 'First name', max: 100 },
    { name: 'lastName', label: 'Last name', max: 100 },
    { name: 'email', label: 'Email', max: 254, type: 'email' },
    { name: 'studyInstitution', label: 'Study institution', max: 200 },
    { name: 'studyProgramme', label: 'Study programme', max: 200 },
    { name: 'studentId', label: 'Student ID', max: 64 },
  ],
};

const EMAIL =
  /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$/;

/** Trims Unicode whitespace including NBSP (String.prototype.trim covers Zs characters). */
export function trimValue(value: string): string {
  return value.trim();
}

export function isEmail(value: string): boolean {
  const at = value.lastIndexOf('@');
  return at > 0 && at <= 64 && EMAIL.test(value);
}

export function codePointLength(value: string): number {
  return Array.from(value).length;
}

export interface ClientCheckInput {
  form: FormType;
  values: Record<string, string>;
  consentRequired: boolean;
  consentGiven: boolean;
  captchaToken: string;
}

/** Returns field → message; message text always contains the field's label. */
export function validateClient(input: ClientCheckInput): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const field of TEXT_FIELDS[input.form]) {
    const value = trimValue(input.values[field.name] ?? '');
    if (value === '') {
      errors[field.name] = `${field.label} is required.`;
    } else if (codePointLength(value) > field.max) {
      errors[field.name] = `${field.label} must be at most ${String(field.max)} characters.`;
    } else if (field.type === 'email' && !isEmail(value)) {
      errors[field.name] = `${field.label}: enter a valid email address.`;
    }
  }
  if (input.consentRequired && !input.consentGiven) {
    errors.consentGiven = 'Consent is required: please accept the consent statement.';
  }
  if (input.captchaToken === '') {
    errors.captchaToken = 'Please confirm the captcha.';
  }
  return errors;
}
