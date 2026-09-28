import { describe, expect, it } from 'vitest';
import { isValidEmail, trimValue, validateFields } from '../fields';

describe('trimValue', () => {
  it('removes surrounding NBSP and Unicode spaces but keeps interior text', () => {
    expect(trimValue(' Špela ')).toBe('Špela');
    expect(trimValue('  ﻿​ Ana  Marija \t')).toBe('Ana  Marija');
  });
});

describe('isValidEmail', () => {
  it.each(['a@example.org', 'first.last+tag@sub.example.co'])('accepts %s', (email) => {
    expect(isValidEmail(email)).toBe(true);
  });
  it.each(['a@b', 'no-at.example.org', 'a b@example.org', 'a@@example.org', '.a@example.org'])(
    'rejects %s',
    (email) => {
      expect(isValidEmail(email)).toBe(false);
    },
  );
});

describe('validateFields', () => {
  it('requires every student field', () => {
    const errors = validateFields('student', { firstName: ' ' });
    expect(Object.keys(errors).sort()).toEqual(
      ['email', 'firstName', 'lastName', 'studentId', 'studyInstitution', 'studyProgramme'].sort(),
    );
  });
  it('accepts a complete external form', () => {
    expect(
      validateFields('external', {
        firstName: 'Špela',
        lastName: 'Novak',
        email: 'spela@example.org',
        organization: 'Org',
      }),
    ).toEqual({});
  });
});
