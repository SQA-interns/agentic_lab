import { describe, expect, it } from 'vitest';
import { codePointLength, fieldError, validate } from './validation';

const name = { field: 'firstName' as const, maxLength: 100 };
const email = { field: 'email' as const, maxLength: 254 };

describe('fieldError', () => {
  it('requires a value after trimming', () => {
    expect(fieldError(name, '')).toBe('REQUIRED');
    expect(fieldError(name, ' \t ')).toBe('REQUIRED');
    expect(fieldError(name, ' Ana ')).toBeNull();
  });

  it('rejects control characters and line separators', () => {
    for (const value of ['An\na', 'A\u0000', 'A b', 'A b']) {
      expect(fieldError(name, value)).toBe('INVALID_CHARACTERS');
    }
  });

  it('counts code points against the limit', () => {
    expect(fieldError(name, 'č'.repeat(100))).toBeNull();
    expect(fieldError(name, 'č'.repeat(101))).toBe('TOO_LONG');
    expect(fieldError(name, '😀'.repeat(100))).toBeNull();
    expect(codePointLength('😀a')).toBe(2);
  });

  it('checks the email format like the backend', () => {
    expect(fieldError(email, 'ana@example.si')).toBeNull();
    expect(fieldError(email, 'š@žabe.si')).toBeNull();
    for (const bad of ['ana', 'ana@', 'ana@example', 'a b@c.si', 'a@b.s', 'a@@b.si', 'a,b@c.si']) {
      expect(fieldError(email, bad)).toBe('INVALID_EMAIL');
    }
  });
});

describe('validate', () => {
  it('checks only the fields of the chosen type plus consent and captcha', () => {
    expect(validate('EXTERNAL', {}, false, null)).toEqual({
      firstName: 'REQUIRED',
      lastName: 'REQUIRED',
      email: 'REQUIRED',
      organization: 'REQUIRED',
      consentGiven: 'CONSENT_REQUIRED',
      captchaToken: 'CAPTCHA_FAILED',
    });
    expect(Object.keys(validate('STUDENT', {}, true, 't'))).toEqual([
      'firstName',
      'lastName',
      'email',
      'studyInstitution',
      'studyProgramme',
      'studentId',
    ]);
  });

  it('passes a complete student', () => {
    expect(
      validate(
        'STUDENT',
        {
          firstName: 'Luka',
          lastName: 'Kranjc',
          email: 'luka@example.si',
          studyInstitution: 'UL',
          studyProgramme: 'FRI',
          studentId: '1',
        },
        true,
        'token',
      ),
    ).toEqual({});
  });
});
