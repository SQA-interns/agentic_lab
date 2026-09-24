import type { FieldDef, OptionCategory, RegistrationType } from './types';

/** Fixed participant fields per variant (FORM_SCHEMA.md). */
export const FIELDS: Record<RegistrationType, readonly FieldDef[]> = {
  EXTERNAL: [
    { name: 'firstName', label: 'First name', kind: 'name', autoComplete: 'given-name' },
    { name: 'lastName', label: 'Last name', kind: 'name', autoComplete: 'family-name' },
    { name: 'email', label: 'Email', kind: 'email', autoComplete: 'email' },
    {
      name: 'organization',
      label: 'Organization / institution',
      kind: 'text',
      autoComplete: 'organization',
    },
  ],
  STUDENT: [
    { name: 'firstName', label: 'First name', kind: 'name', autoComplete: 'given-name' },
    { name: 'lastName', label: 'Last name', kind: 'name', autoComplete: 'family-name' },
    { name: 'email', label: 'Email', kind: 'email', autoComplete: 'email' },
    {
      name: 'studyInstitution',
      label: 'Study institution',
      kind: 'text',
      autoComplete: 'organization',
    },
    { name: 'studyProgramme', label: 'Study programme', kind: 'text', autoComplete: 'off' },
    { name: 'studentId', label: 'Student ID', kind: 'studentId', autoComplete: 'off' },
  ],
};

export const TITLES: Record<RegistrationType, string> = {
  EXTERNAL: 'External participant registration',
  STUDENT: 'Student registration',
};

export const CATEGORY_LABELS: Record<OptionCategory, string> = {
  WORKSHOP: 'Workshops',
  EVENT: 'Events',
  MEAL: 'Meals',
  ACTIVITY: 'Other activities',
};

export const CATEGORY_ORDER: readonly OptionCategory[] = ['WORKSHOP', 'EVENT', 'MEAL', 'ACTIVITY'];
