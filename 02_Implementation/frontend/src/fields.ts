import type { ParticipantType } from './types';

export interface FieldDefinition {
  name: string;
  label: string;
  type: 'text' | 'email';
  maxLength: number;
  autoComplete?: string;
}

const COMMON_START: FieldDefinition[] = [
  {
    name: 'firstName',
    label: 'First name',
    type: 'text',
    maxLength: 100,
    autoComplete: 'given-name',
  },
  {
    name: 'lastName',
    label: 'Last name',
    type: 'text',
    maxLength: 100,
    autoComplete: 'family-name',
  },
  { name: 'email', label: 'Email', type: 'email', maxLength: 254, autoComplete: 'email' },
];

/** Fixed participant fields per form (PRODUCT). All are required. */
export const FORM_FIELDS: Record<ParticipantType, FieldDefinition[]> = {
  external: [
    ...COMMON_START,
    {
      name: 'organization',
      label: 'Organization / institution',
      type: 'text',
      maxLength: 200,
      autoComplete: 'organization',
    },
  ],
  student: [
    ...COMMON_START,
    { name: 'studyInstitution', label: 'Study institution', type: 'text', maxLength: 200 },
    { name: 'studyProgramme', label: 'Study programme', type: 'text', maxLength: 200 },
    { name: 'studentId', label: 'Student ID', type: 'text', maxLength: 64 },
  ],
};

// Leading/trailing Unicode whitespace incl. no-break spaces (JS \s covers U+00A0, U+2007,
// U+202F and U+FEFF); U+200B added explicitly. Mirrors the backend TextNormalizer.
const EDGE_WHITESPACE = /^[\s\u200B]+|[\s\u200B]+$/g;

export function trimValue(value: string): string {
  return value.replace(EDGE_WHITESPACE, '');
}

// Same syntax as the backend (InputPatterns.EMAIL); backend remains authoritative.
const EMAIL =
  /^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$/;

export function isValidEmail(value: string): boolean {
  return value.length <= 254 && EMAIL.test(value);
}

export const ERROR_MESSAGES: Record<string, string> = {
  REQUIRED: 'This field is required.',
  EMAIL_INVALID: 'Enter a valid email address.',
  TOO_LONG: 'This value is too long.',
  FIELD_INVALID: 'This value contains unsupported characters.',
  OPTION_INVALID: 'This option is no longer available.',
  CONSENT_REQUIRED: 'This consent is required.',
  CONSENT_INVALID: 'Unknown consent.',
};

/** Client-side checks for usability only; the backend re-validates everything. */
export function validateFields(
  type: ParticipantType,
  values: Record<string, string>,
): Record<string, string> {
  const errors: Record<string, string> = {};
  for (const field of FORM_FIELDS[type]) {
    const value = trimValue(values[field.name] ?? '');
    if (value === '') {
      errors[field.name] = 'REQUIRED';
    } else if (value.length > field.maxLength) {
      errors[field.name] = 'TOO_LONG';
    } else if (field.type === 'email' && !isValidEmail(value)) {
      errors[field.name] = 'EMAIL_INVALID';
    }
  }
  return errors;
}
