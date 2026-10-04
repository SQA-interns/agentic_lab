import { FORMS, type FieldName, type RegistrationType } from './contract';

// The backend's field rules (specification section 4), applied before submitting (NFR-03).
// The backend stays authoritative.

const EMAIL = /^[^\s@<>()[\],;:"\\]+@[^\s@<>()[\],;:"\\]+\.[^\s@<>()[\],;:"\\]{2,}$/;
const CONTROL = /[\p{Cc}\u2028\u2029]/u;

export type FieldErrors = Partial<Record<FieldName | 'consentGiven' | 'captchaToken', string>>;

export function codePointLength(value: string): number {
  return [...value].length;
}

export function fieldError(
  spec: { field: FieldName; maxLength: number },
  raw: string,
): string | null {
  const value = raw.trim();
  if (value === '') {
    return 'REQUIRED';
  }
  if (CONTROL.test(value)) {
    return 'INVALID_CHARACTERS';
  }
  if (codePointLength(value) > spec.maxLength) {
    return 'TOO_LONG';
  }
  if (spec.field === 'email' && !EMAIL.test(value)) {
    return 'INVALID_EMAIL';
  }
  return null;
}

export function validate(
  type: RegistrationType,
  values: Partial<Record<FieldName, string>>,
  consentGiven: boolean,
  captchaToken: string | null,
): FieldErrors {
  const errors: FieldErrors = {};
  for (const spec of FORMS[type]) {
    const code = fieldError(spec, values[spec.field] ?? '');
    if (code) {
      errors[spec.field] = code;
    }
  }
  if (!consentGiven) {
    errors.consentGiven = 'CONSENT_REQUIRED';
  }
  if (!captchaToken) {
    errors.captchaToken = 'CAPTCHA_FAILED';
  }
  return errors;
}
