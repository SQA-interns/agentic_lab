import { describe, expect, it } from 'vitest';
import type { Consent } from './api';
import { MAX_EMAIL, MAX_TEXT, requiredFields, validate, type FormValues } from './validation';

const CONSENTS: Consent[] = [
  { id: 'dp', text: 'I agree', mandatory: true },
  { id: 'photos', text: 'Photos', mandatory: false },
];

const external: FormValues = {
  firstName: 'Ana',
  lastName: 'Novak',
  email: 'ana@example.si',
  organization: 'IJS',
  studyInstitution: '',
  studyProgramme: '',
  studentId: '',
};

const student: FormValues = {
  ...external,
  organization: '',
  studyInstitution: 'UL',
  studyProgramme: 'RI',
  studentId: '6320',
};

describe('validate', () => {
  it('accepts a complete external registration', () => {
    expect(validate('EXTERNAL', external, CONSENTS, new Set(['dp']), 'tok')).toEqual({});
  });

  it('requires only the fields of the chosen type', () => {
    expect(requiredFields('EXTERNAL')).toEqual(['firstName', 'lastName', 'email', 'organization']);
    expect(requiredFields('STUDENT')).toEqual([
      'firstName',
      'lastName',
      'email',
      'studyInstitution',
      'studyProgramme',
      'studentId',
    ]);
    expect(validate('STUDENT', student, CONSENTS, new Set(['dp']), 'tok')).toEqual({});
    expect(Object.keys(validate('STUDENT', external, CONSENTS, new Set(['dp']), 'tok'))).toEqual([
      'studyInstitution',
      'studyProgramme',
      'studentId',
    ]);
  });

  it('treats whitespace-only values as empty', () => {
    const errors = validate(
      'EXTERNAL',
      { ...external, firstName: '   ' },
      CONSENTS,
      new Set(['dp']),
      'tok',
    );
    expect(errors.firstName).toBe('This field is required.');
  });

  it.each(['ana', 'ana@', '@example.si', 'ana novak@example.si', 'ana@example'])(
    'rejects the email %s',
    (email) => {
      const errors = validate('EXTERNAL', { ...external, email }, CONSENTS, new Set(['dp']), 'tok');
      expect(errors.email).toBe('Enter a valid email address.');
    },
  );

  it('accepts trimmed email and Slovenian letters', () => {
    const values = { ...external, email: ' ana@example.si ', lastName: 'Čučnik-Žagar' };
    expect(validate('EXTERNAL', values, CONSENTS, new Set(['dp']), 'tok')).toEqual({});
  });

  it('limits lengths and control characters', () => {
    expect(
      validate(
        'EXTERNAL',
        { ...external, firstName: 'x'.repeat(MAX_TEXT + 1) },
        CONSENTS,
        new Set(['dp']),
        't',
      ).firstName,
    ).toMatch(/at most 200/);
    expect(
      validate(
        'EXTERNAL',
        { ...external, firstName: 'x'.repeat(MAX_TEXT) },
        CONSENTS,
        new Set(['dp']),
        't',
      ).firstName,
    ).toBeUndefined();
    expect(
      validate(
        'EXTERNAL',
        { ...external, email: `${'a'.repeat(MAX_EMAIL)}@x.si` },
        CONSENTS,
        new Set(['dp']),
        't',
      ).email,
    ).toMatch(/at most 254/);
    expect(
      validate(
        'EXTERNAL',
        { ...external, lastName: 'Novak\nBcc: x' },
        CONSENTS,
        new Set(['dp']),
        't',
      ).lastName,
    ).toMatch(/control characters/);
  });

  it('requires mandatory consents only, and the captcha', () => {
    expect(validate('EXTERNAL', external, CONSENTS, new Set(['photos']), 'tok').consentIds).toBe(
      'Please give the required consent.',
    );
    expect(validate('EXTERNAL', external, CONSENTS, new Set(), '').captchaToken).toBe(
      'Please confirm that you are not a robot.',
    );
  });
});
