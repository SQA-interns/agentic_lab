/**
 * Unit tests — client-side validation (specification § 5.5, AC-G-03).
 *
 * Level justification: this is the layer that must mirror the backend rules. Testing it
 * as a pure function pins the mirroring precisely; an end-to-end test would only show
 * that *some* message appeared.
 */
import { describe, expect, it } from 'vitest';

import type { FieldRule } from '../src/api.js';
import { labelFor, toErrorMap, trimValue, validateField, validateForm } from '../src/validation.js';

const required = (overrides: Partial<FieldRule> = {}): FieldRule => ({
  required: true,
  maxLength: 100,
  ...overrides,
});

describe('trimValue', () => {
  it('matches the backend trimming rule (AC-001-11)', () => {
    expect(trimValue('  Ana  ')).toBe('Ana');
    expect(trimValue(' Ana﻿')).toBe('Ana');
    expect(trimValue('   ')).toBe('');
  });

  it('normalises to NFC so Slovenian characters compare equal (AC-001-10)', () => {
    expect(trimValue('Čenčič')).toBe('Čenčič');
  });
});

describe('validateField', () => {
  it('reports a required field that is empty or whitespace-only', () => {
    expect(validateField('firstName', '', required())).toBe('First name is required.');
    expect(validateField('firstName', '   ', required())).toBe('First name is required.');
  });

  it('accepts a value at the maximum length and rejects one character more', () => {
    expect(validateField('firstName', 'a'.repeat(100), required())).toBeNull();
    expect(validateField('firstName', 'a'.repeat(101), required())).toBe(
      'First name must be at most 100 characters.',
    );
  });

  it('applies the email format rule', () => {
    const rule = required({ maxLength: 254, format: 'email' });
    expect(validateField('email', 'ana@example.org', rule)).toBeNull();
    expect(validateField('email', 'nope', rule)).toBe('Enter a valid email address.');
    expect(validateField('email', 'a@b', rule)).toBe('Enter a valid email address.');
  });

  it('applies a pattern supplied by the backend, so the rule is declared once', () => {
    const rule = required({ maxLength: 50, minLength: 2, pattern: '^[\\p{L}\\p{N}][\\p{L}\\p{N}._\\-/]{1,49}$' });
    expect(validateField('studentId', 'F1234567', rule)).toBeNull();
    expect(validateField('studentId', '-bad', rule)).toBe('Student ID has an invalid format.');
  });

  it('reports a value below the minimum length', () => {
    expect(validateField('studentId', 'a', required({ minLength: 2, maxLength: 50 }))).toBe(
      'Student ID must be at least 2 characters.',
    );
  });

  it('accepts an empty optional field', () => {
    expect(validateField('firstName', '', { required: false, maxLength: 100 })).toBeNull();
  });

  it('validates the trimmed value, so surrounding whitespace never passes or fails a rule', () => {
    expect(validateField('email', '  ana@example.org  ', required({ format: 'email' }))).toBeNull();
  });
});

describe('validateForm', () => {
  const rules = {
    firstName: required(),
    email: required({ maxLength: 254, format: 'email' }),
  };

  it('returns no errors for a complete, consented form', () => {
    expect(
      validateForm(
        { fields: { firstName: 'Ana', email: 'ana@example.org' }, consents: { privacy: true } },
        rules,
        ['privacy'],
      ),
    ).toEqual({});
  });

  it('collects every violation at once (AC-G-04)', () => {
    const errors = validateForm(
      { fields: { firstName: '', email: 'bad' }, consents: { privacy: false } },
      rules,
      ['privacy'],
    );
    expect(Object.keys(errors).sort()).toEqual(['consents.privacy', 'email', 'firstName']);
  });

  it('requires the mandatory consent (AC-001-07)', () => {
    const errors = validateForm(
      { fields: { firstName: 'Ana', email: 'ana@example.org' }, consents: {} },
      rules,
      ['privacy'],
    );
    expect(errors['consents.privacy']).toContain('must agree');
  });
});

describe('toErrorMap', () => {
  it('keys backend errors the same way the form renders them', () => {
    expect(toErrorMap([{ field: 'email', code: 'invalid_email', message: 'Enter a valid email address.' }])).toEqual({
      email: 'Enter a valid email address.',
    });
  });

  it('keeps every option error visible instead of losing all but the last', () => {
    const map = toErrorMap([
      { field: 'selectedOptionIds', code: 'unknown_option', message: 'Unknown A.' },
      { field: 'selectedOptionIds', code: 'unknown_option', message: 'Unknown B.' },
    ]);
    expect(map.selectedOptionIds).toBe('Unknown A. Unknown B.');
  });
});

describe('labelFor', () => {
  it('gives a human label for every fixed field of both variants', () => {
    for (const field of [
      'firstName',
      'lastName',
      'email',
      'organization',
      'studyInstitution',
      'studyProgramme',
      'studentId',
    ]) {
      expect(labelFor(field)).not.toBe(field);
    }
  });
});
