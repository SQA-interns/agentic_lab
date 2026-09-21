/**
 * Component tests — JSON backup store (specification § 3.3, US-005).
 *
 * Level justification: the guarantees here are filesystem guarantees (atomic rename, no
 * leftover temporary file, a self-contained document). They are only meaningful against
 * a real directory, so the component is tested against a temporary one.
 */
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { PersistenceError } from '../../src/domain/errors.js';
import type { Registration } from '../../src/domain/registration.js';
import { JsonBackupStore } from '../../src/infrastructure/backup/jsonBackupStore.js';

let directory: string;
let store: JsonBackupStore;

const registration: Registration = {
  reference: 'REG-20260921-ABCDEFGHJK',
  variant: 'student',
  participant: {
    firstName: 'Žan',
    lastName: 'Šuštaršič',
    email: 'zan@example.org',
    organization: null,
    studyInstitution: 'Univerza v Mariboru',
    studyProgramme: 'Računalništvo',
    studentId: 'F1234567',
  },
  selectedOptions: [{ optionId: 'workshop-ai', group: 'workshops', displayName: 'Workshop: AI' }],
  privacyConsent: true,
  privacyConsentAt: '2026-09-21T10:00:00.000Z',
  createdAt: '2026-09-21T10:00:00.000Z',
};

beforeEach(() => {
  directory = mkdtempSync(path.join(tmpdir(), 'conf-backup-'));
  store = new JsonBackupStore(path.join(directory, 'registrations'));
});

afterEach(() => {
  rmSync(directory, { recursive: true, force: true });
});

describe('JsonBackupStore', () => {
  it('creates its directory if it does not exist', () => {
    expect(existsSync(path.join(directory, 'registrations'))).toBe(true);
  });

  it('names the file after the reference (AC-005-03)', () => {
    expect(store.fileNameFor(registration.reference)).toBe('REG-20260921-ABCDEFGHJK.json');
  });

  it('writes a self-contained document with all fixed fields and options (AC-005-08)', () => {
    const document = store.buildDocument(registration, 'Test Conference');
    const target = store.write(document);

    const parsed = JSON.parse(readFileSync(target, 'utf8')) as Record<string, unknown>;
    expect(parsed.schemaVersion).toBe(1);
    expect(parsed.reference).toBe(registration.reference);
    expect(parsed.variant).toBe('student');
    expect(parsed.createdAt).toBe(registration.createdAt);
    expect(parsed.participant).toEqual(registration.participant);
    expect(parsed.selectedOptions).toEqual(registration.selectedOptions);
    expect(parsed.consent).toEqual({ privacy: true, grantedAt: registration.privacyConsentAt });
    expect(parsed.conference).toEqual({ name: 'Test Conference' });
  });

  it('preserves Unicode in the stored file (AC-001-10)', () => {
    store.write(store.buildDocument(registration, 'Test Conference'));
    const contents = readFileSync(store.pathFor(registration.reference), 'utf8');
    expect(contents).toContain('Šuštaršič');
    expect(contents).toContain('Računalništvo');
  });

  it('leaves no temporary file behind (AC-005-02)', () => {
    store.write(store.buildDocument(registration, 'Test Conference'));
    const files = readdirSync(path.join(directory, 'registrations'));
    expect(files).toEqual(['REG-20260921-ABCDEFGHJK.json']);
  });

  it('reads the file back byte for byte, which is what the organizer receives (AC-007-03)', () => {
    const target = store.write(store.buildDocument(registration, 'Test Conference'));
    expect(store.readRaw(registration.reference).equals(readFileSync(target))).toBe(true);
  });

  it('removes a file when a rolled-back registration must be undone', () => {
    store.write(store.buildDocument(registration, 'Test Conference'));
    store.remove(registration.reference);
    expect(existsSync(store.pathFor(registration.reference))).toBe(false);
  });

  it('removing an absent file is not an error', () => {
    expect(() => store.remove(registration.reference)).not.toThrow();
  });

  it('refuses a reference that is not in the generated format, blocking path traversal', () => {
    expect(() => store.pathFor('../../escape')).toThrow(PersistenceError);
    expect(() => store.readRaw('../../../etc/passwd')).toThrow(PersistenceError);
  });

  it('reports a missing file as a persistence error rather than leaking the path', () => {
    expect(() => store.readRaw('REG-20260921-ZZZZZZZZZZ')).toThrow(PersistenceError);
  });
});
