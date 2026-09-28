export type ParticipantType = 'external' | 'student';

export type OptionGroupKey = 'workshops' | 'events' | 'meals' | 'otherActivities';

export const OPTION_GROUPS: readonly { key: OptionGroupKey; label: string }[] = [
  { key: 'workshops', label: 'Workshops' },
  { key: 'events', label: 'Events' },
  { key: 'meals', label: 'Meals' },
  { key: 'otherActivities', label: 'Other activities' },
];

export interface CatalogOption {
  id: string;
  name: string;
}

export interface ConsentDefinition {
  id: string;
  text: string;
  required: boolean;
}

export interface CaptchaConfig {
  mode: 'recaptcha' | 'test';
  siteKey: string | null;
  testToken: string | null;
}

export interface FormConfig {
  conferenceName: string;
  optionGroups: Record<OptionGroupKey, CatalogOption[]>;
  consents: ConsentDefinition[];
  captcha: CaptchaConfig;
}

export interface FieldErrorDto {
  field: string;
  code: string;
}

export interface Accepted {
  registrationId: string;
  participantType: string;
  submittedAt: string;
  emailStatus: string;
  replayed: boolean;
}
