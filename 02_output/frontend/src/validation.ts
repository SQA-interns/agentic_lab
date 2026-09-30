// Client-side validation mirroring backend rules 3-6 (specification 5, NFR-03). The backend
// validates again; this only gives earlier feedback.
import type { Consent, RegistrationType } from './api';

export interface FormValues {
  firstName: string;
  lastName: string;
  email: string;
  organization: string;
  studyInstitution: string;
  studyProgramme: string;
  studentId: string;
}

export type FieldName = keyof FormValues | 'consentIds' | 'optionIds' | 'captchaToken' | 'type';
export type Errors = Partial<Record<FieldName, string>>;

export const MAX_TEXT = 200;
export const MAX_EMAIL = 254;

const EMAIL = /^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$/;
// eslint-disable-next-line no-control-regex
const CONTROL = /[\u0000-\u001f\u007f-\u009f]/;

export function requiredFields(type: RegistrationType): (keyof FormValues)[] {
  const common: (keyof FormValues)[] = ['firstName', 'lastName', 'email'];
  return type === 'STUDENT'
    ? [...common, 'studyInstitution', 'studyProgramme', 'studentId']
    : [...common, 'organization'];
}

export function validate(
  type: RegistrationType,
  values: FormValues,
  consents: Consent[],
  givenConsents: ReadonlySet<string>,
  captchaToken: string,
): Errors {
  const errors: Errors = {};
  for (const field of requiredFields(type)) {
    const value = values[field].trim();
    const max = field === 'email' ? MAX_EMAIL : MAX_TEXT;
    if (!value) {
      errors[field] = 'This field is required.';
    } else if (value.length > max) {
      errors[field] = `Use at most ${max} characters.`;
    } else if (CONTROL.test(value)) {
      errors[field] = 'Line breaks and control characters are not allowed.';
    }
  }
  if (!errors.email && !EMAIL.test(values.email.trim())) {
    errors.email = 'Enter a valid email address.';
  }
  if (consents.some((c) => c.mandatory && !givenConsents.has(c.id))) {
    errors.consentIds = 'Please give the required consent.';
  }
  if (!captchaToken) {
    errors.captchaToken = 'Please confirm that you are not a robot.';
  }
  return errors;
}
