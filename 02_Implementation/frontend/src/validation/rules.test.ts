import { describe, expect, it } from 'vitest';
import { FIELDS } from '../formConfig';
import { MESSAGES, normalize, validateField, validateForm } from './rules';

describe('validateField', () => {
  it('requires a non-empty value (whitespace only counts as empty)', () => {
    expect(validateField('name', '')).toBe(MESSAGES.required);
    expect(validateField('name', '   ')).toBe(MESSAGES.required);
  });

  it('accepts Slovenian and other Unicode names', () => {
    expect(validateField('name', 'Žiga')).toBeUndefined();
    expect(validateField('name', 'Čeh-Šuštar')).toBeUndefined();
    expect(validateField('name', "O'Neil")).toBeUndefined();
  });

  it('rejects names with markup or digits', () => {
    expect(validateField('name', '<script>')).toBe(MESSAGES.name);
    expect(validateField('name', 'Ana2')).toBe(MESSAGES.name);
  });

  it('validates email format', () => {
    expect(validateField('email', 'ana@example.si')).toBeUndefined();
    expect(validateField('email', ' ana@example.si ')).toBeUndefined();
    expect(validateField('email', 'ana@example')).toBe(MESSAGES.email);
    expect(validateField('email', 'ana.example.si')).toBe(MESSAGES.email);
  });

  it('enforces maximum lengths', () => {
    expect(validateField('text', 'x'.repeat(200))).toBeUndefined();
    expect(validateField('text', 'x'.repeat(201))).toBe(MESSAGES.tooLong(200));
  });

  it('rejects control characters in free text', () => {
    expect(validateField('text', 'FERI\nBcc: x')).toBe(MESSAGES.text);
  });

  it('validates student IDs', () => {
    expect(validateField('studentId', 'E1234567')).toBeUndefined();
    expect(validateField('studentId', 'E12 34')).toBe(MESSAGES.studentId);
  });
});

describe('normalize', () => {
  it('trims and NFC-normalises', () => {
    expect(normalize('  Čeh ')).toBe('Čeh');
  });
});

describe('validateForm', () => {
  it('reports every invalid field and missing consent', () => {
    const errors = validateForm(FIELDS.STUDENT, { firstName: 'Ana' }, false);
    expect(Object.keys(errors).sort()).toEqual(
      [
        'email',
        'lastName',
        'privacyConsent',
        'studentId',
        'studyInstitution',
        'studyProgramme',
      ].sort(),
    );
  });

  it('returns no errors for a valid external form', () => {
    const errors = validateForm(
      FIELDS.EXTERNAL,
      { firstName: 'Ana', lastName: 'Novak', email: 'ana@example.si', organization: 'FERI' },
      true,
    );
    expect(errors).toEqual({});
  });
});
