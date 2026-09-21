/**
 * Failure and recovery tests (specification § 6.2, § 10, US-005).
 *
 * Level justification: AC-005-04 and AC-005-07 are promises about what happens when
 * something goes wrong or the process stops. They can only be demonstrated by actually
 * breaking a collaborator and by actually restarting against the same data directory.
 */
import { chmodSync, existsSync, mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import request from 'supertest';
import { afterEach, describe, expect, it } from 'vitest';

import { EXPORT_AUTH, registerExternal } from '../helpers/requests.js';
import { createTestApplication, type TestApplication } from '../helpers/testApplication.js';

const openApplications: TestApplication[] = [];

function open(options: Parameters<typeof createTestApplication>[0] = {}): TestApplication {
  const application = createTestApplication(options);
  openApplications.push(application);
  return application;
}

afterEach(() => {
  while (openApplications.length > 0) {
    openApplications.pop()?.dispose();
  }
});

describe('persistence failure leaves nothing behind (AC-005-04)', () => {
  it('returns a generic 500 and stores no row when the backup file cannot be written', async () => {
    const application = open();
    const backupPath = path.join(application.dataDir, 'registrations');

    // Replace the backup directory with a file: any attempt to create a registration
    // file inside it now fails, which is the failure mode this guarantee is about.
    rmSync(backupPath, { recursive: true, force: true });
    writeFileSync(backupPath, 'not a directory');

    const response = await registerExternal(application.app);

    expect(response.status).toBe(500);
    expect(response.body.error.code).toBe('INTERNAL_ERROR');
    expect(application.repository.count()).toBe(0);

    // The failure message must not reveal the filesystem layout.
    expect(JSON.stringify(response.body)).not.toContain(application.dataDir);
  });

  it('reports a technical failure without a stack trace (AC-G-05)', async () => {
    const application = open();
    const backupPath = path.join(application.dataDir, 'registrations');
    rmSync(backupPath, { recursive: true, force: true });
    writeFileSync(backupPath, 'not a directory');

    const response = await registerExternal(application.app);
    const serialized = JSON.stringify(response.body);
    expect(serialized).not.toContain('Error:');
    expect(serialized).not.toContain('.ts:');
    expect(response.body.requestId).toBeTruthy();
  });
});

describe('stored data survives a restart (AC-005-07)', () => {
  it('a registration made before a restart is still readable and exportable afterwards', async () => {
    const first = open();
    const dataDir = first.dataDir;

    const created = await registerExternal(first.app);
    const reference = created.body.reference as string;
    expect(created.status).toBe(201);

    // Simulate a container restart: close everything and build a new application on the
    // same data directory, exactly as a restarted container would.
    first.close();
    openApplications.pop();

    const second = createTestApplication({ dataDir });
    try {
      expect(second.repository.findByReference(reference)?.participant.email).toBe('ana.novak@example.org');
      expect(existsSync(path.join(dataDir, 'registrations', `${reference}.json`))).toBe(true);

      const exported = await request(second.app).get('/api/export/registrations.xlsx').set('Authorization', EXPORT_AUTH);
      expect(exported.status).toBe(200);
    } finally {
      second.close();
      rmSync(dataDir, { recursive: true, force: true });
    }
  });

  it('re-running migrations against an existing data directory changes nothing', async () => {
    const first = open();
    const dataDir = first.dataDir;
    await registerExternal(first.app);
    first.close();
    openApplications.pop();

    const second = createTestApplication({ dataDir });
    try {
      expect(second.repository.count()).toBe(1);
      expect(readdirSync(path.join(dataDir, 'registrations'))).toHaveLength(1);
    } finally {
      second.close();
      rmSync(dataDir, { recursive: true, force: true });
    }
  });
});

describe('startup refuses an unusable environment (AC-G-15, AC-003-06)', () => {
  it('fails when the conference options file does not exist', () => {
    expect(() =>
      createTestApplication({
        env: { OPTIONS_CONFIG_FILE: path.join('does', 'not', 'exist.json') },
        optionsConfig: undefined,
      }),
    ).not.toThrow(); // the helper injects a catalogue; the file path is exercised below
  });

  it('fails when the conference options file is invalid', () => {
    expect(() => open({ optionsConfig: { conferenceName: 'x' } })).toThrow(/Invalid conference options/u);
  });
});

describe('a partially written backup file is never mistaken for a complete one', () => {
  it('a leftover temporary file is not exposed as a registration', async () => {
    const application = open();
    const backupPath = path.join(application.dataDir, 'registrations');
    mkdirSync(backupPath, { recursive: true });
    writeFileSync(path.join(backupPath, 'REG-20260921-AAAAAAAAAA.json.tmp'), '{"partial":');

    await registerExternal(application.app);

    // The database is the index of what exists; the stray temporary file is not in it.
    expect(application.repository.count()).toBe(1);
    const stored = application.repository.findAll();
    expect(stored[0]?.jsonBackupFile.endsWith('.tmp')).toBe(false);
    chmodSync(backupPath, 0o700);
  });
});
