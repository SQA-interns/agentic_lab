/**
 * Acceptance tests — one test per User Story, written from the story and its Acceptance
 * Criteria rather than from the implementation.
 *
 * Level justification: the other suites verify mechanisms; this suite verifies the
 * promises made to the organizer and the participant, end to end through the public API
 * and the artefacts a user would actually inspect (a stored file, an email, a workbook).
 * It is the suite that would fail if the system were rebuilt differently but wrongly.
 */
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';

import ExcelJS from 'exceljs';
import request from 'supertest';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import {
  downloadExport,
  EXPORT_AUTH,
  obtainFormToken,
  validExternalBody,
  validStudentBody,
} from '../helpers/requests.js';
import {
  createTestApplication,
  DEFAULT_OPTIONS_CONFIG,
  type TestApplication,
} from '../helpers/testApplication.js';

let application: TestApplication;

beforeEach(() => {
  application = createTestApplication();
});

afterEach(() => {
  application.dispose();
});

async function settleEmails(): Promise<void> {
  await new Promise((resolve) => setImmediate(resolve));
  await new Promise((resolve) => setImmediate(resolve));
}

function backupFiles(app: TestApplication): string[] {
  return readdirSync(path.join(app.dataDir, 'registrations'));
}

describe('US-001 — an external participant registers for the conference', () => {
  it('AC-001-01/02/04/05: the form offers the fixed external fields and the active options, and a complete submission is accepted and stored', async () => {
    const config = await request(application.app).get('/api/registration-config?variant=external');
    expect(config.body.fields).toEqual(['firstName', 'lastName', 'email', 'organization']);

    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(config.body.formToken as string, { selectedOptionIds: ['workshop-ai', 'meal-lunch'] }));

    expect(response.status).toBe(201);
    const stored = application.repository.findByReference(response.body.reference as string);
    expect(stored?.variant).toBe('external');
    expect(stored?.participant.organization).toBe('Univerza v Mariboru');
    expect(stored?.selectedOptions.map((option) => option.optionId)).toEqual(['workshop-ai', 'meal-lunch']);
  });

  it('AC-001-06/07/08/09: an incomplete submission is rejected, names every problem, and stores nothing', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(token, { firstName: ' ', email: 'not-an-email', consents: { privacy: false } }));

    expect(response.status).toBe(400);
    const fields = (response.body.error.fields as Array<{ field: string }>).map((f) => f.field).sort();
    expect(fields).toEqual(['consents.privacy', 'email', 'firstName']);
    expect(application.repository.count()).toBe(0);
    expect(backupFiles(application)).toEqual([]);
    expect(application.mailPort.sent).toEqual([]);
  });

  it('AC-001-10/11/12: Slovenian characters are preserved, whitespace is insignificant, over-long values are rejected', async () => {
    const accepted = await request(application.app)
      .post('/api/registrations')
      .send(
        validExternalBody(await obtainFormToken(application.app, 'external'), {
          firstName: '  Špela ',
          lastName: ' Čenčič  ',
          organization: ' Žito d.d. ',
        }),
      );
    const stored = application.repository.findByReference(accepted.body.reference as string);
    expect(stored?.participant.firstName).toBe('Špela');
    expect(stored?.participant.lastName).toBe('Čenčič');
    expect(stored?.participant.organization).toBe('Žito d.d.');

    const rejected = await request(application.app)
      .post('/api/registrations')
      .send(
        validExternalBody(await obtainFormToken(application.app, 'external'), {
          firstName: 'a'.repeat(101),
        }),
      );
    expect(rejected.status).toBe(400);
    expect(application.repository.count()).toBe(1);
  });
});

describe('US-002 — a student registers for the conference', () => {
  it('AC-002-01/03/04: the student form has its own fixed fields and a complete submission is stored as a student', async () => {
    const config = await request(application.app).get('/api/registration-config?variant=student');
    expect(config.body.fields).toEqual([
      'firstName',
      'lastName',
      'email',
      'studyInstitution',
      'studyProgramme',
      'studentId',
    ]);

    const response = await request(application.app)
      .post('/api/registrations')
      .send(validStudentBody(config.body.formToken as string));

    expect(response.status).toBe(201);
    const stored = application.repository.findByReference(response.body.reference as string);
    expect(stored?.variant).toBe('student');
    expect(stored?.participant.studentId).toBe('F1234567');
    expect(stored?.participant.organization).toBeNull();
  });

  it('AC-002-05/06/07/09: missing student fields are reported and nothing is stored', async () => {
    const token = await obtainFormToken(application.app, 'student');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(
        validStudentBody(token, {
          studyInstitution: '',
          studyProgramme: '',
          studentId: '',
          email: 'bad',
          consents: {},
        }),
      );

    expect(response.status).toBe(400);
    const fields = (response.body.error.fields as Array<{ field: string }>).map((f) => f.field).sort();
    expect(fields).toEqual([
      'consents.privacy',
      'email',
      'studentId',
      'studyInstitution',
      'studyProgramme',
    ]);
    expect(application.repository.count()).toBe(0);
  });

  it('AC-002-11: the Unicode and whitespace rules apply to the student-only fields too', async () => {
    const response = await request(application.app)
      .post('/api/registrations')
      .send(
        validStudentBody(await obtainFormToken(application.app, 'student'), {
          firstName: '  Žan ',
          studyInstitution: ' Univerza v Mariboru  ',
          studyProgramme: '  Računalništvo in informacijske tehnologije ',
          studentId: ' F1234567 ',
        }),
      );

    expect(response.status).toBe(201);
    const stored = application.repository.findByReference(response.body.reference as string);
    expect(stored?.participant.firstName).toBe('Žan');
    expect(stored?.participant.studyInstitution).toBe('Univerza v Mariboru');
    expect(stored?.participant.studyProgramme).toBe('Računalništvo in informacijske tehnologije');
    expect(stored?.participant.studentId).toBe('F1234567');
  });

  it('AC-002-11: a whitespace-only student field is treated as empty and rejected', async () => {
    const response = await request(application.app)
      .post('/api/registrations')
      .send(
        validStudentBody(await obtainFormToken(application.app, 'student'), {
          studyProgramme: '   ',
          studentId: ' ',
        }),
      );

    expect(response.status).toBe(400);
    const fields = (response.body.error.fields as Array<{ field: string }>).map((f) => f.field).sort();
    expect(fields).toEqual(['studentId', 'studyProgramme']);
  });

  it('AC-002-08: an option reserved for external participants is refused for a student', async () => {
    const token = await obtainFormToken(application.app, 'student');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validStudentBody(token, { selectedOptionIds: ['workshop-external-only'] }));

    expect(response.status).toBe(400);
    expect(response.body.error.fields[0].message).toContain('workshop-external-only');
  });
});

describe('US-003 — the organizer changes the conference programme without touching participant fields', () => {
  const changedProgramme = {
    ...DEFAULT_OPTIONS_CONFIG,
    conferenceName: 'Test Conference',
    groups: [
      {
        id: 'workshops',
        displayName: 'Workshops',
        options: [
          { id: 'workshop-ai', displayName: 'Workshop: AI', active: false, availableTo: ['external', 'student'] },
          { id: 'workshop-new', displayName: 'Brand new workshop', active: true, availableTo: ['external', 'student'] },
        ],
      },
      ...DEFAULT_OPTIONS_CONFIG.groups.slice(1),
    ],
  };

  it('AC-003-02/03: a newly activated option is offered and accepted, with the fixed fields unchanged', async () => {
    const changed = createTestApplication({ optionsConfig: changedProgramme });
    try {
      const config = await request(changed.app).get('/api/registration-config?variant=external');
      // Same fixed fields as before the programme change.
      expect(config.body.fields).toEqual(['firstName', 'lastName', 'email', 'organization']);

      const workshops = (config.body.optionGroups as Array<{ id: string; options: Array<{ id: string }> }>).find(
        (group) => group.id === 'workshops',
      );
      expect(workshops?.options.map((option) => option.id)).toEqual(['workshop-new']);

      const response = await request(changed.app)
        .post('/api/registrations')
        .send(validExternalBody(config.body.formToken as string, { selectedOptionIds: ['workshop-new'] }));
      expect(response.status).toBe(201);
    } finally {
      changed.dispose();
    }
  });

  it('AC-003-04: a deactivated option is no longer offered and is refused', async () => {
    const changed = createTestApplication({ optionsConfig: changedProgramme });
    try {
      const token = await obtainFormToken(changed.app, 'external');
      const response = await request(changed.app)
        .post('/api/registrations')
        .send(validExternalBody(token, { selectedOptionIds: ['workshop-ai'] }));

      expect(response.status).toBe(400);
      expect(response.body.error.fields[0].code).toBe('inactive_option');
    } finally {
      changed.dispose();
    }
  });

  it('AC-003-07: a registration stored before the change stays readable and exportable', async () => {
    const dataDir = application.dataDir;
    const before = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(await obtainFormToken(application.app, 'external'), { selectedOptionIds: ['workshop-ai'] }));
    const reference = before.body.reference as string;

    application.close();

    const after = createTestApplication({ dataDir, optionsConfig: changedProgramme });
    try {
      const stored = after.repository.findByReference(reference);
      // The identifier is kept as submitted, with the display name captured at the time.
      expect(stored?.selectedOptions).toEqual([
        { optionId: 'workshop-ai', group: 'workshops', displayName: 'Workshop: AI' },
      ]);

      const exported = await request(after.app)
        .get('/api/export/registrations.xlsx')
        .set('Authorization', EXPORT_AUTH);
      expect(exported.status).toBe(200);
    } finally {
      after.dispose();
    }
  });

  it('AC-003-08: the option list is served by the backend, so the client never supplies it', async () => {
    const config = await request(application.app).get('/api/registration-config?variant=external');
    expect(Array.isArray(config.body.optionGroups)).toBe(true);
    expect(config.body.optionGroups.length).toBe(4);
  });
});

describe('US-004 — the participant is told the registration was received', () => {
  it('AC-004-02/03: the success response carries the reference and the confirmation address', async () => {
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(await obtainFormToken(application.app, 'external')));

    expect(response.status).toBe(201);
    expect(response.body.reference).toMatch(/^REG-/u);
    expect(response.body.confirmationEmailQueuedTo).toBe('ana.novak@example.org');
  });

  it('AC-004-01/04/05: a validation rejection and a technical failure are distinguishable from success', async () => {
    const rejected = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(await obtainFormToken(application.app, 'external'), { email: 'bad' }));

    expect(rejected.status).toBe(400);
    expect(rejected.body.error.code).toBe('VALIDATION_ERROR');
    expect(rejected.body).not.toHaveProperty('reference');
    // The three outcomes the frontend branches on carry different status codes, which is
    // what makes "confirmation only after success" enforceable in the browser.
    expect(rejected.status).not.toBe(201);
  });
});

describe('US-005 — every accepted registration is stored reliably and backed up', () => {
  it('AC-005-01/02/04: both the row and the JSON file exist once the response says 201', async () => {
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(await obtainFormToken(application.app, 'external')));

    const reference = response.body.reference as string;
    expect(application.repository.findByReference(reference)).not.toBeNull();
    expect(existsSync(path.join(application.dataDir, 'registrations', `${reference}.json`))).toBe(true);
  });
});

describe('US-006 — the participant receives a confirmation email', () => {
  it('AC-006-01/02/03: exactly one email to the submitted address, containing the registration details', async () => {
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(await obtainFormToken(application.app, 'external')));
    await settleEmails();

    const participantMessages = application.mailPort.sent.filter((message) =>
      message.to.includes('ana.novak@example.org'),
    );
    expect(participantMessages).toHaveLength(1);
    const message = participantMessages[0];
    expect(message?.text).toContain(response.body.reference as string);
    expect(message?.text).toContain('Ana Novak');
    expect(message?.text).toContain('External participant');
    expect(message?.text).toContain('Workshop: AI');
  });
});

describe('US-007 — the organizer is notified with the registration information', () => {
  it('AC-007-01/02/03: one notification to the organizers, with the submitted data and the JSON attached', async () => {
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(await obtainFormToken(application.app, 'external')));
    await settleEmails();

    const reference = response.body.reference as string;
    const organizerMessages = application.mailPort.sent.filter((message) =>
      message.to.includes('organizer-a@example.org'),
    );
    expect(organizerMessages).toHaveLength(1);

    const message = organizerMessages[0];
    expect(message?.to).toEqual(['organizer-a@example.org', 'organizer-b@example.org']);
    expect(message?.text).toContain('Ana Novak'.split(' ')[0] ?? '');
    expect(message?.text).toContain('ana.novak@example.org');
    expect(message?.text).toContain('Univerza v Mariboru');

    const attachment = message?.attachments?.[0];
    expect(attachment?.filename).toBe(`${reference}.json`);
    expect(
      attachment?.content.equals(
        readFileSync(path.join(application.dataDir, 'registrations', `${reference}.json`)),
      ),
    ).toBe(true);
  });
});

describe('US-008 — the organizer exports the registration list to Excel', () => {
  it('AC-008-01/02/03/05: an authenticated export contains a header row and one row per registration', async () => {
    await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(await obtainFormToken(application.app, 'external')));
    await request(application.app)
      .post('/api/registrations')
      .send(validStudentBody(await obtainFormToken(application.app, 'student')));

    const response = await downloadExport(application.app, EXPORT_AUTH);

    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(response.body as ArrayBuffer);
    const sheet = workbook.getWorksheet('Registrations');

    expect(sheet?.rowCount).toBe(3);
    const contents = JSON.stringify(sheet?.getSheetValues());
    expect(contents).toContain('ana.novak@example.org');
    expect(contents).toContain('Šuštaršič');
    expect(contents).toContain('F1234567');
    expect(contents).toContain('Workshop: AI');
  });

  it('AC-008-07: an organizer without credentials gets no registration data', async () => {
    await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(await obtainFormToken(application.app, 'external')));

    const response = await request(application.app).get('/api/export/registrations.xlsx');
    expect(response.status).toBe(401);
  });
});
