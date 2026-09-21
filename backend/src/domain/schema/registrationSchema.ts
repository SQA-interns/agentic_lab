/**
 * Registration request schema and field rules (specification § 5.2).
 *
 * This module is the single declaration of the input contract. The API route validates
 * against it, and the same rule table is published to the frontend through
 * `GET /api/registration-config` so the browser cannot drift from the backend (AC-G-03).
 */
import { z } from 'zod';

import { REGISTRATION_VARIANTS, type RegistrationVariant } from '../registration.js';
import { normalizeEmail, normalizeOptionIds, normalizeText } from '../normalize.js';
import type { FieldError } from '../errors.js';

export const MAX_LENGTHS = Object.freeze({
  firstName: 100,
  lastName: 100,
  email: 254,
  organization: 200,
  studyInstitution: 200,
  studyProgramme: 200,
  studentId: 50,
  optionId: 64,
  selectedOptionIds: 50,
});

/**
 * Pragmatic email rule: exactly one `@`, a non-empty local part, and a dotted domain.
 * Deliberately stricter than RFC 5322 (which allows quoted local parts and comments) and
 * looser than a delivery check — the confirmation email is the real proof of validity.
 */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)+$/u;

/** Student ID: starts alphanumeric, then letters, digits, `. _ - /`; 2–50 characters. */
export const STUDENT_ID_PATTERN = /^[\p{L}\p{N}][\p{L}\p{N}._\-/]{1,49}$/u;

export interface FieldRule {
  readonly required: boolean;
  readonly maxLength: number;
  readonly format?: 'email';
  readonly pattern?: string;
  readonly minLength?: number;
}

/** Field rules per variant, published verbatim to the frontend. */
export const FIELD_RULES: Readonly<Record<RegistrationVariant, Readonly<Record<string, FieldRule>>>> =
  Object.freeze({
    external: Object.freeze({
      firstName: { required: true, maxLength: MAX_LENGTHS.firstName },
      lastName: { required: true, maxLength: MAX_LENGTHS.lastName },
      email: { required: true, maxLength: MAX_LENGTHS.email, format: 'email' as const },
      organization: { required: true, maxLength: MAX_LENGTHS.organization },
    }),
    student: Object.freeze({
      firstName: { required: true, maxLength: MAX_LENGTHS.firstName },
      lastName: { required: true, maxLength: MAX_LENGTHS.lastName },
      email: { required: true, maxLength: MAX_LENGTHS.email, format: 'email' as const },
      studyInstitution: { required: true, maxLength: MAX_LENGTHS.studyInstitution },
      studyProgramme: { required: true, maxLength: MAX_LENGTHS.studyProgramme },
      studentId: {
        required: true,
        minLength: 2,
        maxLength: MAX_LENGTHS.studentId,
        pattern: STUDENT_ID_PATTERN.source,
      },
    }),
  });

/**
 * Shape of the accepted request body.
 *
 * `strict()` rejects unknown properties, which blocks mass assignment and catches
 * client/server drift early (AC-G-06). Every text field is `unknown` here because the
 * per-field rules are applied after normalisation, not before.
 */
export const registrationRequestShape = z
  .object({
    variant: z.unknown(),
    firstName: z.unknown().optional(),
    lastName: z.unknown().optional(),
    email: z.unknown().optional(),
    organization: z.unknown().optional(),
    studyInstitution: z.unknown().optional(),
    studyProgramme: z.unknown().optional(),
    studentId: z.unknown().optional(),
    selectedOptionIds: z.unknown().optional(),
    consents: z.unknown().optional(),
    formToken: z.unknown().optional(),
    website: z.unknown().optional(),
  })
  .strict();

/** A registration request that passed field validation but not yet option validation. */
export interface ValidatedRegistrationInput {
  readonly variant: RegistrationVariant;
  readonly firstName: string;
  readonly lastName: string;
  readonly email: string;
  readonly organization: string | null;
  readonly studyInstitution: string | null;
  readonly studyProgramme: string | null;
  readonly studentId: string | null;
  readonly selectedOptionIds: readonly string[];
  readonly privacyConsent: true;
  readonly formToken: string;
}

export type FieldValidationResult =
  | { readonly ok: true; readonly value: ValidatedRegistrationInput }
  | { readonly ok: false; readonly errors: readonly FieldError[] };

interface TextFieldSpec {
  readonly field: string;
  readonly maxLength: number;
  readonly minLength?: number;
  readonly pattern?: RegExp;
  readonly patternCode?: string;
  readonly patternMessage?: string;
  readonly label: string;
}

const TEXT_FIELD_SPECS: Readonly<Record<RegistrationVariant, readonly TextFieldSpec[]>> =
  Object.freeze({
    external: [
      { field: 'firstName', maxLength: MAX_LENGTHS.firstName, label: 'First name' },
      { field: 'lastName', maxLength: MAX_LENGTHS.lastName, label: 'Last name' },
      {
        field: 'organization',
        maxLength: MAX_LENGTHS.organization,
        label: 'Organization / institution',
      },
    ],
    student: [
      { field: 'firstName', maxLength: MAX_LENGTHS.firstName, label: 'First name' },
      { field: 'lastName', maxLength: MAX_LENGTHS.lastName, label: 'Last name' },
      {
        field: 'studyInstitution',
        maxLength: MAX_LENGTHS.studyInstitution,
        label: 'Study institution',
      },
      {
        field: 'studyProgramme',
        maxLength: MAX_LENGTHS.studyProgramme,
        label: 'Study programme',
      },
      {
        field: 'studentId',
        maxLength: MAX_LENGTHS.studentId,
        minLength: 2,
        pattern: STUDENT_ID_PATTERN,
        patternCode: 'invalid_format',
        patternMessage:
          'Enter a valid student ID: 2 to 50 letters or digits, optionally with . _ - / after the first character.',
        label: 'Student ID',
      },
    ],
  });

function validateTextField(
  spec: TextFieldSpec,
  raw: unknown,
  errors: FieldError[],
): string | null {
  const { value, hasControlCharacters } = normalizeText(raw);

  if (hasControlCharacters) {
    errors.push({
      field: spec.field,
      code: 'invalid_characters',
      message: `${spec.label} contains characters that are not allowed.`,
    });
    return null;
  }
  if (value === null) {
    errors.push({
      field: spec.field,
      code: 'required',
      message: `${spec.label} is required.`,
    });
    return null;
  }
  if (value.length > spec.maxLength) {
    errors.push({
      field: spec.field,
      code: 'too_long',
      message: `${spec.label} must be at most ${spec.maxLength} characters.`,
    });
    return null;
  }
  if (spec.minLength !== undefined && value.length < spec.minLength) {
    errors.push({
      field: spec.field,
      code: 'too_short',
      message: `${spec.label} must be at least ${spec.minLength} characters.`,
    });
    return null;
  }
  if (spec.pattern !== undefined && !spec.pattern.test(value)) {
    errors.push({
      field: spec.field,
      code: spec.patternCode ?? 'invalid_format',
      message: spec.patternMessage ?? `${spec.label} has an invalid format.`,
    });
    return null;
  }
  return value;
}

function validateEmail(raw: unknown, errors: FieldError[]): string | null {
  const { value, hasControlCharacters } = normalizeEmail(raw);

  if (hasControlCharacters) {
    errors.push({
      field: 'email',
      code: 'invalid_characters',
      message: 'Email contains characters that are not allowed.',
    });
    return null;
  }
  if (value === null) {
    errors.push({ field: 'email', code: 'required', message: 'Email is required.' });
    return null;
  }
  if (value.length > MAX_LENGTHS.email) {
    errors.push({
      field: 'email',
      code: 'too_long',
      message: `Email must be at most ${MAX_LENGTHS.email} characters.`,
    });
    return null;
  }
  if (!EMAIL_PATTERN.test(value)) {
    errors.push({
      field: 'email',
      code: 'invalid_email',
      message: 'Enter a valid email address.',
    });
    return null;
  }
  return value;
}

function validateOptionIds(raw: unknown, errors: FieldError[]): readonly string[] {
  if (raw === undefined || raw === null) {
    return [];
  }
  if (!Array.isArray(raw)) {
    errors.push({
      field: 'selectedOptionIds',
      code: 'invalid_type',
      message: 'Selected options must be a list of option identifiers.',
    });
    return [];
  }
  if (raw.some((entry) => typeof entry !== 'string')) {
    errors.push({
      field: 'selectedOptionIds',
      code: 'invalid_type',
      message: 'Every selected option must be an option identifier.',
    });
    return [];
  }
  if (raw.length > MAX_LENGTHS.selectedOptionIds) {
    errors.push({
      field: 'selectedOptionIds',
      code: 'too_many',
      message: `At most ${MAX_LENGTHS.selectedOptionIds} options can be selected.`,
    });
    return [];
  }

  const normalized = normalizeOptionIds(raw);
  if (normalized.some((id) => id.length > MAX_LENGTHS.optionId)) {
    errors.push({
      field: 'selectedOptionIds',
      code: 'too_long',
      message: `Option identifiers must be at most ${MAX_LENGTHS.optionId} characters.`,
    });
    return [];
  }
  return normalized;
}

function validateConsent(raw: unknown, errors: FieldError[]): boolean {
  const consents = typeof raw === 'object' && raw !== null ? (raw as Record<string, unknown>) : {};
  if (consents.privacy !== true) {
    errors.push({
      field: 'consents.privacy',
      code: 'consent_required',
      message: 'You must agree to the processing of your personal data to register.',
    });
    return false;
  }
  return true;
}

/** Map Zod shape issues onto the public field-error contract. */
function toShapeErrors(issues: readonly { code: string; path: PropertyKey[] }[]): FieldError[] {
  return issues.map((issue) => {
    const unknownField = issue.code === 'unrecognized_keys';
    const keys = 'keys' in issue ? ((issue as { keys?: string[] }).keys ?? []) : [];
    return {
      field: unknownField && keys.length > 0 ? keys.join(', ') : (issue.path.join('.') || 'body'),
      code: unknownField ? 'unknown_field' : 'invalid_type',
      message: unknownField
        ? `Unknown field: ${keys.join(', ')}.`
        : 'The field has an unexpected type.',
    };
  });
}

function validateVariant(raw: unknown, errors: FieldError[]): RegistrationVariant | null {
  const { value } = normalizeText(raw);
  if (value === null || !(REGISTRATION_VARIANTS as readonly string[]).includes(value)) {
    errors.push({
      field: 'variant',
      code: 'invalid_variant',
      message: `Registration variant must be one of: ${REGISTRATION_VARIANTS.join(', ')}.`,
    });
    return null;
  }
  return value as RegistrationVariant;
}

function validateFormToken(raw: unknown, errors: FieldError[]): string | null {
  const { value } = normalizeText(raw);
  if (value === null) {
    errors.push({
      field: 'formToken',
      code: 'required',
      message: 'The form token is missing. Please reload the form.',
    });
  }
  return value;
}

/**
 * Keep only the fields that belong to the submitted variant.
 *
 * Fields of the other variant are dropped here and can therefore never be stored,
 * whatever the client sent (AC-001-13, AC-002-12).
 */
function pickVariantFields(
  variant: RegistrationVariant,
  values: Record<string, string | null>,
): Pick<
  ValidatedRegistrationInput,
  'organization' | 'studyInstitution' | 'studyProgramme' | 'studentId'
> {
  if (variant === 'external') {
    return {
      organization: values.organization ?? null,
      studyInstitution: null,
      studyProgramme: null,
      studentId: null,
    };
  }
  return {
    organization: null,
    studyInstitution: values.studyInstitution ?? null,
    studyProgramme: values.studyProgramme ?? null,
    studentId: values.studentId ?? null,
  };
}

/**
 * Validate one registration request body against the field rules.
 *
 * Every violation is collected rather than short-circuiting, so a single response tells
 * the participant about all their mistakes at once (AC-G-04). The one exception is an
 * unusable variant: without it there is no field set to validate against.
 */
export function validateRegistrationFields(body: unknown): FieldValidationResult {
  const errors: FieldError[] = [];

  const parsed = registrationRequestShape.safeParse(body);
  if (!parsed.success) {
    return { ok: false, errors: toShapeErrors(parsed.error.issues) };
  }

  const raw = parsed.data;
  const typedVariant = validateVariant(raw.variant, errors);
  if (typedVariant === null) {
    return { ok: false, errors };
  }

  const values: Record<string, string | null> = {};
  for (const spec of TEXT_FIELD_SPECS[typedVariant]) {
    values[spec.field] = validateTextField(
      spec,
      (raw as Record<string, unknown>)[spec.field],
      errors,
    );
  }

  const email = validateEmail(raw.email, errors);
  const selectedOptionIds = validateOptionIds(raw.selectedOptionIds, errors);
  validateConsent(raw.consents, errors);
  const formToken = validateFormToken(raw.formToken, errors);

  if (errors.length > 0) {
    return { ok: false, errors };
  }

  return {
    ok: true,
    value: {
      variant: typedVariant,
      firstName: values.firstName as string,
      lastName: values.lastName as string,
      email: email as string,
      ...pickVariantFields(typedVariant, values),
      selectedOptionIds,
      privacyConsent: true,
      formToken: formToken as string,
    },
  };
}
