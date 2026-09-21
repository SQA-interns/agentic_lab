/**
 * Integration tests — the registration use case end to end inside the process
 * (specification § 6, US-005, US-006, US-007).
 *
 * Level justification: the guarantees of US-005 span three collaborators — the database,
 * the filesystem and the mail port. They cannot be shown by testing any one of them
 * alone, and they are invisible from the HTTP contract, which only says "201". These
 * tests assert on what is actually on disk and what was actually sent.
 */
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';

import request from 'supertest';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { obtainFormToken, registerExternal, registerStudent, validExternalBody } from '../helpers/requests.js';
import { createTestApplication, type TestApplication } from '../helpers/testApplication.js';

let application: TestApplication;

beforeEach(() => {
  application = createTestApplication();
});

afterEach(() => {
  application.dispose();
});

function backupDir(app: TestApplication): string {
  return path.join(app.dataDir, 'registrations');
}

/** The service dispatches emails after the response; let those promises settle. */
async function settleEmails(): Promise<void> {
  await new Promise((resolve) => setImmediate(resolve));
  await new Promise((resolve) => setImmediate(resolve));
}

describe('an accepted registration is stored twice (US-005)', () => {
  it('writes a database row and a JSON file with the same reference (AC-005-01, AC-005-02)', async () => {
    const response = await registerExternal(application.app);
    const reference = response.body.reference as string;

    const stored = application.repository.findByReference(reference);
    expect(stored).not.toBeNull();
    expect(stored?.participant.email).toBe('ana.novak@example.org');

    const file = path.join(backupDir(application), `${reference}.json`);
    expect(existsSync(file)).toBe(true);
    const document = JSON.parse(readFileSync(file, 'utf8')) as { reference: string };
    expect(document.reference).toBe(reference);
  });

  it('gives the JSON file a name derived from the reference (AC-005-03)', async () => {
    const response = await registerExternal(application.app);
    expect(readdirSync(backupDir(application))).toEqual([`${response.body.reference as string}.json`]);
  });

  it('writes a JSON file that alone reconstructs the registration (AC-005-08)', async () => {
    const response = await registerStudent(application.app);
    const document = JSON.parse(
      readFileSync(path.join(backupDir(application), `${response.body.reference as string}.json`), 'utf8'),
    ) as Record<string, unknown>;

    expect(document.variant).toBe('student');
    expect(document.participant).toEqual({
      firstName: 'Žan',
      lastName: 'Šuštaršič',
      email: 'zan.sustarsic@student.example.org',
      organization: null,
      studyInstitution: 'Univerza v Mariboru',
      studyProgramme: 'Računalništvo in informacijske tehnologije',
      studentId: 'F1234567',
    });
    expect(document.selectedOptions).toEqual([
      { optionId: 'workshop-ai', group: 'workshops', displayName: 'Workshop: AI' },
      { optionId: 'meal-lunch', group: 'meals', displayName: 'Lunch' },
    ]);
    expect(document.consent).toMatchObject({ privacy: true });
    expect(typeof document.createdAt).toBe('string');
  });

  it('trims and normalises before storing (AC-001-11, AC-001-10)', async () => {
    const response = await registerExternal(application.app, {
      firstName: '  Špela  ',
      lastName: ' Čenčič ',
      email: '  Spela.Cencic@Example.ORG ',
    });
    const stored = application.repository.findByReference(response.body.reference as string);
    expect(stored?.participant.firstName).toBe('Špela');
    expect(stored?.participant.lastName).toBe('Čenčič');
    expect(stored?.participant.email).toBe('spela.cencic@example.org');
  });

  it('stores exactly the submitted options, no more and no fewer (AC-001-05)', async () => {
    const response = await registerExternal(application.app, {
      selectedOptionIds: ['meal-lunch', 'workshop-ai'],
    });
    const stored = application.repository.findByReference(response.body.reference as string);
    expect(stored?.selectedOptions.map((o) => o.optionId)).toEqual(['meal-lunch', 'workshop-ai']);
  });

  it('collapses a duplicated option instead of storing it twice (AC-003-09)', async () => {
    const response = await registerExternal(application.app, {
      selectedOptionIds: ['workshop-ai', 'workshop-ai', ' workshop-ai '],
    });
    const stored = application.repository.findByReference(response.body.reference as string);
    expect(stored?.selectedOptions).toHaveLength(1);
  });

  it('gives concurrent submissions distinct references and distinct files (AC-005-06)', async () => {
    const tokens = await Promise.all(
      Array.from({ length: 5 }, () => obtainFormToken(application.app, 'external')),
    );
    const responses = await Promise.all(
      tokens.map((token, index) =>
        request(application.app)
          .post('/api/registrations')
          .send(validExternalBody(token, { email: `p${index}@example.org` })),
      ),
    );

    expect(responses.every((r) => r.status === 201)).toBe(true);
    const references = new Set(responses.map((r) => r.body.reference as string));
    expect(references.size).toBe(5);
    expect(application.repository.count()).toBe(5);
    expect(readdirSync(backupDir(application))).toHaveLength(5);
  });
});

describe('a rejected registration stores nothing (AC-001-09, AC-002-09, AC-006-03, AC-007-04)', () => {
  it.each([
    ['invalid email', { email: 'nope' }],
    ['missing consent', { consents: { privacy: false } }],
    ['unknown option', { selectedOptionIds: ['nope'] }],
    ['filled honeypot', { website: 'spam' }],
  ])('%s leaves no row, no file and no email', async (_label, overrides) => {
    const response = await registerExternal(application.app, overrides);
    await settleEmails();

    expect(response.status).toBe(400);
    expect(application.repository.count()).toBe(0);
    expect(readdirSync(backupDir(application))).toEqual([]);
    expect(application.mailPort.sent).toEqual([]);
  });
});

describe('emails are produced for accepted registrations (US-006, US-007)', () => {
  it('sends exactly one participant email and one organizer email (AC-006-01, AC-007-01)', async () => {
    const response = await registerExternal(application.app);
    await settleEmails();

    expect(application.mailPort.sent).toHaveLength(2);
    const [participant, organizer] = application.mailPort.sent;
    expect(participant?.to).toEqual(['ana.novak@example.org']);
    expect(organizer?.to).toEqual(['organizer-a@example.org', 'organizer-b@example.org']);
    expect(participant?.subject).toContain(response.body.reference as string);
  });

  it('keeps the two recipient lists separate (AC-007-07)', async () => {
    await registerExternal(application.app);
    await settleEmails();

    const [participant, organizer] = application.mailPort.sent;
    expect(participant?.to).not.toContain('organizer-a@example.org');
    expect(organizer?.to).not.toContain('ana.novak@example.org');
    // The participant address is only a Reply-To, so organizers can answer directly.
    expect(organizer?.replyTo).toBe('ana.novak@example.org');
    expect(participant?.attachments).toBeUndefined();
  });

  it('attaches the stored JSON file byte for byte (AC-007-03)', async () => {
    const response = await registerExternal(application.app);
    await settleEmails();

    const reference = response.body.reference as string;
    const organizer = application.mailPort.sent[1];
    const attachment = organizer?.attachments?.[0];

    expect(attachment?.filename).toBe(`${reference}.json`);
    expect(attachment?.contentType).toBe('application/json');
    expect(attachment?.content.equals(readFileSync(path.join(backupDir(application), `${reference}.json`)))).toBe(
      true,
    );
  });

  it('lists the selected option display names in the participant email (AC-006-02)', async () => {
    await registerStudent(application.app);
    await settleEmails();

    const participant = application.mailPort.sent[0];
    expect(participant?.text).toContain('Workshop: AI');
    expect(participant?.text).toContain('Lunch');
    expect(participant?.text).toContain('Žan Šuštaršič');
  });
});

describe('email failure never loses a registration (AC-005-05, AC-006-04, AC-007-05)', () => {
  it('still returns 201 and keeps both stored artefacts when sending fails', async () => {
    application.mailPort.failWith = new Error('SMTP unavailable');

    const response = await registerExternal(application.app);
    await settleEmails();

    expect(response.status).toBe(201);
    const reference = response.body.reference as string;
    expect(application.repository.findByReference(reference)).not.toBeNull();
    expect(existsSync(path.join(backupDir(application), `${reference}.json`))).toBe(true);
    expect(application.mailPort.sent).toEqual([]);
  });
});

describe('the registration reference is the link between the artefacts', () => {
  it('is the same in the response, the database row, the file name and the email', async () => {
    const response = await registerExternal(application.app);
    await settleEmails();

    const reference = response.body.reference as string;
    expect(application.repository.findByReference(reference)?.jsonBackupFile).toBe(`${reference}.json`);
    expect(readdirSync(backupDir(application))).toContain(`${reference}.json`);
    expect(application.mailPort.sent[0]?.text).toContain(reference);
    expect(application.mailPort.sent[1]?.text).toContain(reference);
  });
});
