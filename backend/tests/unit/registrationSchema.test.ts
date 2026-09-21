/**
 * Unit tests — field validation rules (specification § 5.2, AC-G-03, AC-G-04).
 *
 * Level justification: the rule table is the contract both the API and the frontend
 * depend on. Testing it directly pins each rule and each error code to a specific input,
 * which an HTTP-level test can only do indirectly, and lets the boundary values
 * (exactly at, one over the maximum) be checked without dozens of requests.
 */
import { describe, expect, it } from 'vitest';

import { validateRegistrationFields } from '../../src/domain/schema/registrationSchema.js';

const externalBase = {
  variant: 'external',
  firstName: 'Ana',
  lastName: 'Novak',
  email: 'ana@example.org',
  organization: 'Univerza v Mariboru',
  consents: { privacy: true },
  formToken: 'token',
};

const studentBase = {
  variant: 'student',
  firstName: 'Žan',
  lastName: 'Šuštaršič',
  email: 'zan@example.org',
  studyInstitution: 'Univerza v Mariboru',
  studyProgramme: 'Računalništvo',
  studentId: 'F1234567',
  consents: { privacy: true },
  formToken: 'token',
};

function codesFor(result: ReturnType<typeof validateRegistrationFields>, field: string): string[] {
  return result.ok ? [] : result.errors.filter((e) => e.field === field).map((e) => e.code);
}

describe('validateRegistrationFields — accepted input', () => {
  it('accepts a complete external registration', () => {
    const result = validateRegistrationFields(externalBase);
    expect(result.ok).toBe(true);
  });

  it('accepts a complete student registration', () => {
    const result = validateRegistrationFields(studentBase);
    expect(result.ok).toBe(true);
  });

  it('trims values before storing them (AC-001-11)', () => {
    const result = validateRegistrationFields({ ...externalBase, firstName: '  Ana  ' });
    expect(result.ok && result.value.firstName).toBe('Ana');
  });

  it('accepts a registration with no options selected (AC-001-03)', () => {
    const result = validateRegistrationFields({ ...externalBase, selectedOptionIds: [] });
    expect(result.ok && result.value.selectedOptionIds).toEqual([]);
  });
});

describe('validateRegistrationFields — variant handling', () => {
  it('rejects a missing or unknown variant', () => {
    expect(codesFor(validateRegistrationFields({ ...externalBase, variant: undefined }), 'variant')).toEqual([
      'invalid_variant',
    ]);
    expect(codesFor(validateRegistrationFields({ ...externalBase, variant: 'guest' }), 'variant')).toEqual([
      'invalid_variant',
    ]);
  });

  it('discards student fields sent to the external variant (AC-001-13)', () => {
    const result = validateRegistrationFields({
      ...externalBase,
      studyInstitution: 'Injected',
      studyProgramme: 'Injected',
      studentId: 'X1',
    });
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.value.variant).toBe('external');
      expect(result.value.studyInstitution).toBeNull();
      expect(result.value.studyProgramme).toBeNull();
      expect(result.value.studentId).toBeNull();
    }
  });

  it('discards the organization sent to the student variant (AC-002-12)', () => {
    const result = validateRegistrationFields({ ...studentBase, organization: 'Injected' });
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.value.variant).toBe('student');
      expect(result.value.organization).toBeNull();
    }
  });

  it('requires the external-only field on the external variant (AC-001-06)', () => {
    expect(
      codesFor(validateRegistrationFields({ ...externalBase, organization: '   ' }), 'organization'),
    ).toEqual(['required']);
  });

  it('requires every student-only field on the student variant (AC-002-05)', () => {
    const result = validateRegistrationFields({
      ...studentBase,
      studyInstitution: '',
      studyProgramme: '   ',
      studentId: undefined,
    });
    expect(codesFor(result, 'studyInstitution')).toEqual(['required']);
    expect(codesFor(result, 'studyProgramme')).toEqual(['required']);
    expect(codesFor(result, 'studentId')).toEqual(['required']);
  });
});

describe('validateRegistrationFields — field rules', () => {
  it('reports every violation in one pass (AC-G-04)', () => {
    const result = validateRegistrationFields({
      variant: 'external',
      firstName: '',
      lastName: '',
      email: 'not-an-email',
      organization: '',
      consents: { privacy: false },
      formToken: '',
    });
    expect(result.ok).toBe(false);
    if (!result.ok) {
      const fields = result.errors.map((e) => e.field).sort();
      expect(fields).toEqual([
        'consents.privacy',
        'email',
        'firstName',
        'formToken',
        'lastName',
        'organization',
      ]);
    }
  });

  it.each([
    ['no-at-sign'],
    ['a@'],
    ['@example.org'],
    ['a b@example.org'],
    ['a@b'],
    ['a@@b.org'],
    ['a@.org'],
  ])('rejects the invalid email %s (AC-001-08)', (email) => {
    expect(codesFor(validateRegistrationFields({ ...externalBase, email }), 'email')).toEqual([
      'invalid_email',
    ]);
  });

  it.each([['a@b.org'], ['ana.novak+tag@sub.example.co.uk'], ['ana_novak@example-uni.org']])(
    'accepts the valid email %s',
    (email) => {
      expect(validateRegistrationFields({ ...externalBase, email }).ok).toBe(true);
    },
  );

  it('accepts a value exactly at the maximum length and rejects one character more (AC-001-12)', () => {
    expect(validateRegistrationFields({ ...externalBase, firstName: 'a'.repeat(100) }).ok).toBe(true);
    expect(codesFor(validateRegistrationFields({ ...externalBase, firstName: 'a'.repeat(101) }), 'firstName')).toEqual(
      ['too_long'],
    );
  });

  it.each([['F1234567'], ['12345678'], ['ab'], ['E-12/34'], ['Š1234567']])(
    'accepts the student ID %s (AC-002-10)',
    (studentId) => {
      expect(validateRegistrationFields({ ...studentBase, studentId }).ok).toBe(true);
    },
  );

  it.each([['-1234'], ['/abc'], ['a b'], ['a'], ['a'.repeat(51)], ['ab$cd']])(
    'rejects the invalid student ID %s (AC-002-10)',
    (studentId) => {
      const codes = codesFor(validateRegistrationFields({ ...studentBase, studentId }), 'studentId');
      expect(codes.length).toBe(1);
      expect(['invalid_format', 'too_long', 'too_short']).toContain(codes[0]);
    },
  );

  it('rejects control characters in a text field (AC-G-09)', () => {
    expect(codesFor(validateRegistrationFields({ ...externalBase, lastName: 'No\nvak' }), 'lastName')).toEqual([
      'invalid_characters',
    ]);
  });
});

describe('validateRegistrationFields — consent', () => {
  it.each([[undefined], [{}], [{ privacy: false }], [{ privacy: 'true' }], [{ privacy: 1 }], [null]])(
    'rejects consent value %j (AC-001-07, AC-002-06)',
    (consents) => {
      expect(codesFor(validateRegistrationFields({ ...externalBase, consents }), 'consents.privacy')).toEqual([
        'consent_required',
      ]);
    },
  );
});

describe('validateRegistrationFields — request shape', () => {
  it('rejects unknown properties (AC-G-06)', () => {
    const result = validateRegistrationFields({ ...externalBase, isAdmin: true });
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.errors[0]?.code).toBe('unknown_field');
      expect(result.errors[0]?.message).toContain('isAdmin');
    }
  });

  it('rejects a non-array option list (AC-G-06)', () => {
    expect(
      codesFor(validateRegistrationFields({ ...externalBase, selectedOptionIds: 'workshop-ai' }), 'selectedOptionIds'),
    ).toEqual(['invalid_type']);
  });

  it('rejects option lists containing non-strings', () => {
    expect(
      codesFor(validateRegistrationFields({ ...externalBase, selectedOptionIds: ['a', 7] }), 'selectedOptionIds'),
    ).toEqual(['invalid_type']);
  });

  it('rejects an oversized option list', () => {
    const many = Array.from({ length: 51 }, (_, index) => `option-${index}`);
    expect(
      codesFor(validateRegistrationFields({ ...externalBase, selectedOptionIds: many }), 'selectedOptionIds'),
    ).toEqual(['too_many']);
  });

  it('rejects an over-long option identifier', () => {
    expect(
      codesFor(
        validateRegistrationFields({ ...externalBase, selectedOptionIds: ['a'.repeat(65)] }),
        'selectedOptionIds',
      ),
    ).toEqual(['too_long']);
  });

  it('rejects a body that is not an object', () => {
    expect(validateRegistrationFields('a string').ok).toBe(false);
    expect(validateRegistrationFields(null).ok).toBe(false);
  });
});
