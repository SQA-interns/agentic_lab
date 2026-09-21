/**
 * Component tests — SQLite repository (specification § 3.2, § 6.2).
 *
 * Level justification: these exercise one component against the real engine on a
 * temporary file. A mocked database would only prove that the mock was called; the
 * behaviour that matters here — the unique constraint, the composite primary key, the
 * cascade and the transaction boundary — lives in SQLite itself.
 */
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import type { Database } from 'better-sqlite3';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import type { Registration } from '../../src/domain/registration.js';
import { openDatabase } from '../../src/infrastructure/db/database.js';
import { runMigrations } from '../../src/infrastructure/db/migrations.js';
import { RegistrationRepository } from '../../src/infrastructure/db/registrationRepository.js';

let directory: string;
let db: Database;
let repository: RegistrationRepository;

function makeRegistration(overrides: Partial<Registration> = {}): Registration {
  return {
    reference: 'REG-20260921-ABCDEFGHJK',
    variant: 'external',
    participant: {
      firstName: 'Ana',
      lastName: 'Novak',
      email: 'ana@example.org',
      organization: 'Univerza v Mariboru',
      studyInstitution: null,
      studyProgramme: null,
      studentId: null,
    },
    selectedOptions: [{ optionId: 'workshop-ai', group: 'workshops', displayName: 'Workshop: AI' }],
    privacyConsent: true,
    privacyConsentAt: '2026-09-21T10:00:00.000Z',
    createdAt: '2026-09-21T10:00:00.000Z',
    ...overrides,
  };
}

beforeEach(() => {
  directory = mkdtempSync(path.join(tmpdir(), 'conf-repo-'));
  db = openDatabase({ file: path.join(directory, 'test.db') });
  repository = new RegistrationRepository(db);
});

afterEach(() => {
  db.close();
  rmSync(directory, { recursive: true, force: true });
});

describe('migrations', () => {
  it('creates the schema and is idempotent', () => {
    // Running again on an initialised database must apply nothing, which is what makes
    // restarting a container against an existing volume safe.
    expect(runMigrations(db)).toBe(0);
  });

  it('records the applied version', () => {
    const rows = db.prepare('SELECT version FROM schema_migrations').all();
    expect(rows).toEqual([{ version: 1 }]);
  });
});

describe('RegistrationRepository', () => {
  it('stores and reads back a registration with its options (AC-005-01)', () => {
    const registration = makeRegistration();
    repository.insertWithinTransaction(registration, 'REG-20260921-ABCDEFGHJK.json', () => undefined);

    const stored = repository.findByReference(registration.reference);
    expect(stored?.participant.firstName).toBe('Ana');
    expect(stored?.variant).toBe('external');
    expect(stored?.selectedOptions).toEqual(registration.selectedOptions);
    expect(stored?.privacyConsentAt).toBe(registration.privacyConsentAt);
    expect(stored?.jsonBackupFile).toBe('REG-20260921-ABCDEFGHJK.json');
  });

  it('preserves Unicode values exactly (AC-001-10)', () => {
    repository.insertWithinTransaction(
      makeRegistration({
        participant: {
          firstName: 'Žan',
          lastName: 'Šuštaršič',
          email: 'zan@example.org',
          organization: 'Čebelarstvo d.o.o.',
          studyInstitution: null,
          studyProgramme: null,
          studentId: null,
        },
      }),
      'file.json',
      () => undefined,
    );
    const stored = repository.findAll()[0];
    expect(stored?.participant.lastName).toBe('Šuštaršič');
    expect(stored?.participant.organization).toBe('Čebelarstvo d.o.o.');
  });

  it('stores values containing SQL metacharacters verbatim, without executing them (AC-G-09)', () => {
    const hostile = "'; DROP TABLE registrations; --";
    repository.insertWithinTransaction(
      makeRegistration({
        participant: {
          firstName: hostile,
          lastName: 'Novak',
          email: 'a@b.org',
          organization: 'X',
          studyInstitution: null,
          studyProgramme: null,
          studentId: null,
        },
      }),
      'file.json',
      () => undefined,
    );
    expect(repository.findAll()[0]?.participant.firstName).toBe(hostile);
    expect(repository.count()).toBe(1);
  });

  it('rejects a duplicate reference (AC-005-06)', () => {
    repository.insertWithinTransaction(makeRegistration(), 'a.json', () => undefined);
    expect(() =>
      repository.insertWithinTransaction(makeRegistration(), 'b.json', () => undefined),
    ).toThrow();
    expect(repository.count()).toBe(1);
  });

  it('rolls back the row when the side effect throws (AC-005-04)', () => {
    expect(() =>
      repository.insertWithinTransaction(makeRegistration(), 'a.json', () => {
        throw new Error('backup write failed');
      }),
    ).toThrow('backup write failed');

    expect(repository.count()).toBe(0);
    expect(db.prepare('SELECT COUNT(*) AS n FROM registration_options').get()).toEqual({ n: 0 });
  });

  it('stores one row per option, in submission order', () => {
    repository.insertWithinTransaction(
      makeRegistration({
        selectedOptions: [
          { optionId: 'b', group: 'events', displayName: 'B' },
          { optionId: 'a', group: 'workshops', displayName: 'A' },
        ],
      }),
      'a.json',
      () => undefined,
    );
    expect(repository.findAll()[0]?.selectedOptions.map((o) => o.optionId)).toEqual(['b', 'a']);
  });

  it('cannot store the same option twice for one registration (AC-003-09)', () => {
    expect(() =>
      repository.insertWithinTransaction(
        makeRegistration({
          selectedOptions: [
            { optionId: 'a', group: 'workshops', displayName: 'A' },
            { optionId: 'a', group: 'workshops', displayName: 'A' },
          ],
        }),
        'a.json',
        () => undefined,
      ),
    ).toThrow();
  });

  it('returns registrations oldest first, which is the export order (AC-008-02)', () => {
    repository.insertWithinTransaction(
      makeRegistration({ reference: 'REG-20260921-BBBBBBBBBB', createdAt: '2026-09-21T12:00:00.000Z' }),
      'b.json',
      () => undefined,
    );
    repository.insertWithinTransaction(
      makeRegistration({ reference: 'REG-20260921-AAAAAAAAAA', createdAt: '2026-09-21T09:00:00.000Z' }),
      'a.json',
      () => undefined,
    );
    expect(repository.findAll().map((r) => r.reference)).toEqual([
      'REG-20260921-AAAAAAAAAA',
      'REG-20260921-BBBBBBBBBB',
    ]);
  });

  it('reports an unknown reference as absent', () => {
    expect(repository.findByReference('REG-20260921-ZZZZZZZZZZ')).toBeNull();
    expect(repository.existsByReference('REG-20260921-ZZZZZZZZZZ')).toBe(false);
  });

  it('applies the durability pragmas the specification requires', () => {
    expect(String(db.pragma('journal_mode', { simple: true })).toLowerCase()).toBe('wal');
    expect(db.pragma('foreign_keys', { simple: true })).toBe(1);
    expect(db.pragma('synchronous', { simple: true })).toBe(2);
  });
});
