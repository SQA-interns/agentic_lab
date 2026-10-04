// Types and texts of the REST and UI contracts (docs/02_contracts/registration-api.openapi.yaml,
// registration-form.ui.json).

export type RegistrationType = 'EXTERNAL' | 'STUDENT';
export type Category = 'workshop' | 'event' | 'meal' | 'other';
export type FieldName =
  | 'firstName'
  | 'lastName'
  | 'email'
  | 'organization'
  | 'studyInstitution'
  | 'studyProgramme'
  | 'studentId';

export interface FormOption {
  id: string;
  name: string;
  category: Category;
}

export interface FormConfig {
  conferenceName: string;
  consent: { id: string; text: string };
  options: FormOption[];
  captcha: { testMode: boolean; siteKey: string | null };
}

export interface FieldSpec {
  field: FieldName;
  label: string;
  inputType: 'text' | 'email';
  autocomplete: string;
  maxLength: number;
}

const firstName: FieldSpec = {
  field: 'firstName',
  label: 'First name',
  inputType: 'text',
  autocomplete: 'given-name',
  maxLength: 100,
};
const lastName: FieldSpec = {
  field: 'lastName',
  label: 'Last name',
  inputType: 'text',
  autocomplete: 'family-name',
  maxLength: 100,
};
const email: FieldSpec = {
  field: 'email',
  label: 'Email',
  inputType: 'email',
  autocomplete: 'email',
  maxLength: 254,
};

export const FORMS: Record<RegistrationType, FieldSpec[]> = {
  EXTERNAL: [
    firstName,
    lastName,
    email,
    {
      field: 'organization',
      label: 'Organization / institution',
      inputType: 'text',
      autocomplete: 'organization',
      maxLength: 200,
    },
  ],
  STUDENT: [
    firstName,
    lastName,
    email,
    {
      field: 'studyInstitution',
      label: 'Study institution',
      inputType: 'text',
      autocomplete: 'off',
      maxLength: 200,
    },
    {
      field: 'studyProgramme',
      label: 'Study programme',
      inputType: 'text',
      autocomplete: 'off',
      maxLength: 200,
    },
    {
      field: 'studentId',
      label: 'Student ID',
      inputType: 'text',
      autocomplete: 'off',
      maxLength: 50,
    },
  ],
};

export const TYPE_CHOICES: { type: RegistrationType; label: string }[] = [
  { type: 'EXTERNAL', label: 'External participant' },
  { type: 'STUDENT', label: 'Student' },
];

export const OPTION_GROUPS: { category: Category; legend: string }[] = [
  { category: 'workshop', legend: 'Workshops' },
  { category: 'event', legend: 'Events' },
  { category: 'meal', legend: 'Meals' },
  { category: 'other', legend: 'Other activities' },
];

export const CAPTCHA_TEST_LABEL = 'I am not a robot (test mode)';
export const CAPTCHA_TEST_TOKEN = 'test-pass';

export const MESSAGES: Record<string, string> = {
  REQUIRED: 'This field is required.',
  TOO_LONG: 'This value is too long.',
  INVALID_CHARACTERS: 'This value contains characters that are not allowed.',
  INVALID_EMAIL: 'Enter a valid email address.',
  UNKNOWN_OPTION: 'One of the selected options is not available.',
  INACTIVE_OPTION: 'One of the selected options is not available.',
  DUPLICATE_OPTION: 'An option was selected more than once.',
  CONSENT_REQUIRED: 'You must give this consent to register.',
  CAPTCHA_FAILED: 'Please confirm that you are not a robot.',
  NOT_ALLOWED: 'This field does not belong to the selected registration type.',
  EMAIL_ALREADY_REGISTERED:
    'This email address is already registered. Please contact the organizers if you need to change your registration.',
  RATE_LIMITED: 'Too many attempts. Please wait a minute and try again.',
  STORAGE_UNAVAILABLE: 'Your registration could not be saved. Please try again later.',
  GENERIC: 'Something went wrong. Please try again later.',
};

export function message(code: string): string {
  return MESSAGES[code] ?? MESSAGES.GENERIC;
}
