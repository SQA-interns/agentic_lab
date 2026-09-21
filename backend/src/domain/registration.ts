/**
 * Registration domain model (specification § 3.1).
 *
 * The fixed participant fields come from FORM_SCHEMA.md and never change; the option
 * groups are configuration-driven (US-003).
 */

/** The two registration variants required by PROJECT_CONSTRAINTS.md. */
export const REGISTRATION_VARIANTS = ['external', 'student'] as const;
export type RegistrationVariant = (typeof REGISTRATION_VARIANTS)[number];

/** The four configurable option groups named in FORM_SCHEMA.md. */
export const OPTION_GROUPS = ['workshops', 'events', 'meals', 'other'] as const;
export type OptionGroupId = (typeof OPTION_GROUPS)[number];

/** A conference option as it was selected, with the display name captured at submission time. */
export interface SelectedOption {
  readonly optionId: string;
  readonly group: OptionGroupId;
  readonly displayName: string;
}

/** Fixed participant fields; variant-inapplicable fields are always null. */
export interface Participant {
  readonly firstName: string;
  readonly lastName: string;
  readonly email: string;
  /** External variant only. */
  readonly organization: string | null;
  /** Student variant only. */
  readonly studyInstitution: string | null;
  /** Student variant only. */
  readonly studyProgramme: string | null;
  /** Student variant only. */
  readonly studentId: string | null;
}

/** A registration that has been accepted and stored. */
export interface Registration {
  readonly reference: string;
  readonly variant: RegistrationVariant;
  readonly participant: Participant;
  readonly selectedOptions: readonly SelectedOption[];
  readonly privacyConsent: true;
  readonly privacyConsentAt: string;
  readonly createdAt: string;
}

/** Per-variant fixed field lists, used by the config endpoint and the export. */
export const FIXED_FIELDS: Readonly<Record<RegistrationVariant, readonly string[]>> = Object.freeze({
  external: Object.freeze(['firstName', 'lastName', 'email', 'organization']),
  student: Object.freeze([
    'firstName',
    'lastName',
    'email',
    'studyInstitution',
    'studyProgramme',
    'studentId',
  ]),
});

export function isRegistrationVariant(value: unknown): value is RegistrationVariant {
  return typeof value === 'string' && (REGISTRATION_VARIANTS as readonly string[]).includes(value);
}
