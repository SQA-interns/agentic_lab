/**
 * Unit tests — conference options configuration (specification § 3.4, US-003).
 *
 * Level justification: the whole point of US-003 is that the programme changes without a
 * code change. These tests drive the catalogue with several different configurations,
 * which is cheap here and would require restarting the application at any higher level.
 */
import { describe, expect, it } from 'vitest';

import { parseOptionsConfig } from '../../src/config/optionsConfig.js';
import { ConfigurationError } from '../../src/domain/errors.js';
import { DEFAULT_OPTIONS_CONFIG } from '../helpers/testApplication.js';

function configWith(groups: unknown): unknown {
  return {
    conferenceName: 'Test Conference',
    privacyConsentText: 'I agree.',
    groups,
  };
}

const emptyGroups = [
  { id: 'workshops', displayName: 'Workshops', options: [] },
  { id: 'events', displayName: 'Events', options: [] },
  { id: 'meals', displayName: 'Meals', options: [] },
  { id: 'other', displayName: 'Other', options: [] },
];

describe('parseOptionsConfig — valid configurations', () => {
  it('accepts a configuration with all four groups empty', () => {
    const catalogue = parseOptionsConfig(configWith(emptyGroups), 'test');
    expect(catalogue.activeGroupsFor('external').every((group) => group.options.length === 0)).toBe(true);
  });

  it('offers only active options (AC-003-04)', () => {
    const catalogue = parseOptionsConfig(DEFAULT_OPTIONS_CONFIG, 'test');
    const workshopIds = catalogue
      .activeGroupsFor('external')
      .find((group) => group.id === 'workshops')
      ?.options.map((option) => option.id);
    expect(workshopIds).toEqual(['workshop-ai', 'workshop-external-only']);
  });

  it('offers only options available to the requested variant (AC-002-02)', () => {
    const catalogue = parseOptionsConfig(DEFAULT_OPTIONS_CONFIG, 'test');
    const studentWorkshops = catalogue
      .activeGroupsFor('student')
      .find((group) => group.id === 'workshops')
      ?.options.map((option) => option.id);
    expect(studentWorkshops).toEqual(['workshop-ai']);
  });

  it('defaults availableTo to both variants when it is omitted', () => {
    const catalogue = parseOptionsConfig(
      configWith([
        { id: 'workshops', displayName: 'W', options: [{ id: 'w-1', displayName: 'W1', active: true }] },
        ...emptyGroups.slice(1),
      ]),
      'test',
    );
    expect(catalogue.resolve('w-1', 'external').ok).toBe(true);
    expect(catalogue.resolve('w-1', 'student').ok).toBe(true);
  });

  it('exposes the conference name and consent text used by the form and the emails', () => {
    const catalogue = parseOptionsConfig(DEFAULT_OPTIONS_CONFIG, 'test');
    expect(catalogue.conferenceName).toBe('Test Conference');
    expect(catalogue.privacyConsentText).toContain('personal data');
  });
});

describe('parseOptionsConfig — resolution', () => {
  const catalogue = parseOptionsConfig(DEFAULT_OPTIONS_CONFIG, 'test');

  it('resolves an active option to its group and display name', () => {
    const result = catalogue.resolve('workshop-ai', 'external');
    expect(result).toEqual({
      ok: true,
      option: { optionId: 'workshop-ai', group: 'workshops', displayName: 'Workshop: AI' },
    });
  });

  it('reports an unknown identifier (AC-003-05)', () => {
    expect(catalogue.resolve('does-not-exist', 'external')).toEqual({ ok: false, reason: 'unknown' });
  });

  it('reports an inactive identifier (AC-003-04)', () => {
    expect(catalogue.resolve('workshop-retired', 'external')).toEqual({ ok: false, reason: 'inactive' });
  });

  it('reports an identifier the variant may not select (AC-002-08)', () => {
    expect(catalogue.resolve('workshop-external-only', 'student')).toEqual({
      ok: false,
      reason: 'not_available_for_variant',
    });
  });

  it('still knows the display name of an inactive option, so old registrations stay readable (AC-003-07)', () => {
    expect(catalogue.displayNameOf('workshop-retired')).toBe('Retired workshop');
    expect(catalogue.displayNameOf('never-existed')).toBeNull();
  });
});

describe('parseOptionsConfig — invalid configurations (AC-003-06)', () => {
  it.each([
    [
      'duplicate identifiers across groups',
      configWith([
        { id: 'workshops', displayName: 'W', options: [{ id: 'dup', displayName: 'A', active: true }] },
        { id: 'events', displayName: 'E', options: [{ id: 'dup', displayName: 'B', active: true }] },
        ...emptyGroups.slice(2),
      ]),
    ],
    [
      'a missing identifier',
      configWith([
        { id: 'workshops', displayName: 'W', options: [{ displayName: 'A', active: true }] },
        ...emptyGroups.slice(1),
      ]),
    ],
    [
      'a missing display name',
      configWith([
        { id: 'workshops', displayName: 'W', options: [{ id: 'a', active: true }] },
        ...emptyGroups.slice(1),
      ]),
    ],
    [
      'a missing active flag',
      configWith([
        { id: 'workshops', displayName: 'W', options: [{ id: 'a', displayName: 'A' }] },
        ...emptyGroups.slice(1),
      ]),
    ],
    ['a missing group', configWith(emptyGroups.slice(1))],
    [
      'an unknown group id',
      configWith([{ id: 'sponsors', displayName: 'S', options: [] }, ...emptyGroups.slice(1)]),
    ],
    ['a missing conference name', { privacyConsentText: 'x', groups: emptyGroups }],
    ['a missing consent text', { conferenceName: 'x', groups: emptyGroups }],
    ['an unknown top-level key', { conferenceName: 'x', privacyConsentText: 'y', groups: emptyGroups, extra: 1 }],
    ['a non-object configuration', 'not a configuration'],
  ])('rejects %s', (_label, raw) => {
    expect(() => parseOptionsConfig(raw, 'test')).toThrow(ConfigurationError);
  });

  it('names the problem in the error message so an operator can fix the file', () => {
    expect(() =>
      parseOptionsConfig(
        configWith([
          { id: 'workshops', displayName: 'W', options: [{ id: 'dup', displayName: 'A', active: true }] },
          { id: 'events', displayName: 'E', options: [{ id: 'dup', displayName: 'B', active: true }] },
          ...emptyGroups.slice(2),
        ]),
        'test',
      ),
    ).toThrow(/duplicate option identifier "dup"/u);
  });
});
