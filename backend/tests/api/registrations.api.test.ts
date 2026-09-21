/**
 * REST API / contract tests — POST /api/registrations (specification § 4.3).
 *
 * Level justification: the frontend branches on status code and error code, and the
 * confirmation rule ("only after a success response") is meaningless unless the response
 * contract holds. These tests pin every documented status, code and envelope shape.
 */
import request from 'supertest';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { createTestApplication, type TestApplication } from '../helpers/testApplication.js';
import {
  obtainFormToken,
  registerExternal,
  registerStudent,
  validExternalBody,
} from '../helpers/requests.js';

let application: TestApplication;

beforeEach(() => {
  application = createTestApplication();
});

afterEach(() => {
  application.dispose();
});

describe('POST /api/registrations — success contract', () => {
  it('returns 201 with the documented payload over REST/JSON (AC-G-01, AC-004-02, AC-004-03)', async () => {
    const response = await registerExternal(application.app);

    expect(response.status).toBe(201);
    expect(response.body).toMatchObject({
      variant: 'external',
      email: 'ana.novak@example.org',
      confirmationEmailQueuedTo: 'ana.novak@example.org',
    });
    expect(response.body.reference).toMatch(/^REG-\d{8}-[0-9A-Z]{10}$/u);
    expect(response.body.createdAt).toMatch(/^\d{4}-\d{2}-\d{2}T/u);
    expect(response.body.selectedOptions).toEqual([
      { optionId: 'workshop-ai', group: 'workshops', displayName: 'Workshop: AI' },
    ]);
  });

  it('accepts a student registration (AC-002-03)', async () => {
    const response = await registerStudent(application.app);
    expect(response.status).toBe(201);
    expect(response.body.variant).toBe('student');
    expect(response.body.selectedOptions.map((o: { optionId: string }) => o.optionId)).toEqual([
      'workshop-ai',
      'meal-lunch',
    ]);
  });

  it('accepts a registration with no options selected (AC-001-03)', async () => {
    const response = await registerExternal(application.app, { selectedOptionIds: [] });
    expect(response.status).toBe(201);
    expect(response.body.selectedOptions).toEqual([]);
  });

  it('accepts a registration with the option list omitted entirely', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const body = validExternalBody(token);
    delete body.selectedOptionIds;
    const response = await request(application.app).post('/api/registrations').send(body);
    expect(response.status).toBe(201);
  });
});

describe('POST /api/registrations — validation contract (AC-G-02, AC-G-04, AC-G-05)', () => {
  it('returns 400 VALIDATION_ERROR listing every offending field', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(token, { firstName: '', lastName: '  ', email: 'nope', organization: '' }));

    expect(response.status).toBe(400);
    expect(response.body.error.code).toBe('VALIDATION_ERROR');
    const fields = (response.body.error.fields as Array<{ field: string }>).map((f) => f.field).sort();
    expect(fields).toEqual(['email', 'firstName', 'lastName', 'organization']);
  });

  it('returns a message for each field without exposing internals', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(token, { email: 'nope' }));

    const serialized = JSON.stringify(response.body);
    expect(response.body.error.fields[0].message).toBe('Enter a valid email address.');
    expect(serialized).not.toMatch(/at [A-Za-z]+\s\(/u);
    expect(serialized).not.toContain('SELECT');
    expect(serialized).not.toContain('node_modules');
  });

  it('rejects a missing mandatory consent (AC-001-07)', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(token, { consents: { privacy: false } }));
    expect(response.status).toBe(400);
    expect(response.body.error.fields[0].field).toBe('consents.privacy');
  });

  it('names the offending identifier for an unknown option (AC-G-07, AC-003-05)', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(token, { selectedOptionIds: ['not-a-real-option'] }));

    expect(response.status).toBe(400);
    expect(response.body.error.fields[0]).toMatchObject({
      field: 'selectedOptionIds',
      code: 'unknown_option',
    });
    expect(response.body.error.fields[0].message).toContain('not-a-real-option');
  });

  it('names the offending identifier for an inactive option (AC-G-07, AC-003-04)', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(token, { selectedOptionIds: ['workshop-retired'] }));

    expect(response.body.error.fields[0].code).toBe('inactive_option');
    expect(response.body.error.fields[0].message).toContain('workshop-retired');
  });

  it('rejects an option a student may not select (AC-002-08)', async () => {
    const token = await obtainFormToken(application.app, 'student');
    const response = await request(application.app)
      .post('/api/registrations')
      .send({
        variant: 'student',
        firstName: 'Žan',
        lastName: 'Novak',
        email: 'zan@example.org',
        studyInstitution: 'UM',
        studyProgramme: 'RIT',
        studentId: 'F1234567',
        selectedOptionIds: ['workshop-external-only'],
        consents: { privacy: true },
        formToken: token,
        website: '',
      });

    expect(response.status).toBe(400);
    expect(response.body.error.fields[0].code).toBe('option_not_available');
    expect(response.body.error.fields[0].message).toContain('workshop-external-only');
  });

  it('reports every offending option identifier, not only the first', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(token, { selectedOptionIds: ['nope-one', 'workshop-retired', 'nope-two'] }));

    expect(response.body.error.fields).toHaveLength(3);
  });
});

describe('POST /api/registrations — malformed requests (AC-G-06)', () => {
  it('rejects a body that is not valid JSON', async () => {
    const response = await request(application.app)
      .post('/api/registrations')
      .set('Content-Type', 'application/json')
      .send('{ this is not json');
    expect(response.status).toBe(400);
    expect(response.body.error.code).toBe('MALFORMED_REQUEST');
  });

  it('rejects an empty body', async () => {
    const response = await request(application.app)
      .post('/api/registrations')
      .set('Content-Type', 'application/json')
      .send('');
    expect(response.status).toBe(400);
    expect(['MALFORMED_REQUEST', 'VALIDATION_ERROR']).toContain(response.body.error.code);
  });

  it('rejects a JSON array body', async () => {
    const response = await request(application.app).post('/api/registrations').send([1, 2, 3]);
    expect(response.status).toBe(400);
    expect(response.body.error.code).toBe('MALFORMED_REQUEST');
  });

  it('rejects unknown properties', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(token, { role: 'admin' }));
    expect(response.status).toBe(400);
    expect(response.body.error.fields[0].code).toBe('unknown_field');
  });

  it('rejects a body above the size limit with 413 (AC-G-11)', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const response = await request(application.app)
      .post('/api/registrations')
      .set('Content-Type', 'application/json')
      .send(JSON.stringify(validExternalBody(token, { firstName: 'a'.repeat(40_000) })));
    expect(response.status).toBe(413);
    expect(response.body.error.code).toBe('PAYLOAD_TOO_LARGE');
  });

  it('does not accept a non-JSON content type', async () => {
    const response = await request(application.app)
      .post('/api/registrations')
      .set('Content-Type', 'text/plain')
      .send('variant=external');
    expect(response.status).toBe(400);
  });
});

describe('POST /api/registrations — anti-automation contract (AC-G-10)', () => {
  it('rejects a filled honeypot with an opaque error', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(token, { website: 'http://spam.example' }));

    expect(response.status).toBe(400);
    expect(response.body.error.code).toBe('ANTI_AUTOMATION_FAILED');
    expect(response.body.error).not.toHaveProperty('fields');
    expect(application.repository.count()).toBe(0);
  });

  it('rejects a missing form token', async () => {
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(''));
    expect(response.status).toBe(400);
    expect(response.body.error.fields[0].field).toBe('formToken');
  });

  it('rejects a forged form token', async () => {
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody('forged.token'));
    expect(response.status).toBe(400);
    expect(response.body.error.code).toBe('ANTI_AUTOMATION_FAILED');
  });

  it('rejects a token issued for the other variant', async () => {
    const studentToken = await obtainFormToken(application.app, 'student');
    const response = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(studentToken));
    expect(response.body.error.code).toBe('ANTI_AUTOMATION_FAILED');
  });

  it('rejects a replayed token: one token, one registration', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const first = await request(application.app).post('/api/registrations').send(validExternalBody(token));
    const second = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(token, { email: 'other@example.org' }));

    expect(first.status).toBe(201);
    expect(second.status).toBe(400);
    expect(second.body.error.code).toBe('ANTI_AUTOMATION_FAILED');
    expect(application.repository.count()).toBe(1);
  });

  it('returns the same message for every anti-automation reason', async () => {
    const token = await obtainFormToken(application.app, 'external');
    const honeypot = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody(token, { website: 'x' }));
    const forged = await request(application.app)
      .post('/api/registrations')
      .send(validExternalBody('forged.token'));

    expect(honeypot.body.error.message).toBe(forged.body.error.message);
  });
});

describe('POST /api/registrations — rate limiting (AC-G-10)', () => {
  it('returns 429 with Retry-After once the limit is exceeded', async () => {
    const app = createTestApplication({ env: { RATE_LIMIT_REGISTRATION_MAX: '2' } });
    try {
      const responses = [];
      for (let attempt = 0; attempt < 4; attempt += 1) {
        const token = await obtainFormToken(app.app, 'external');
        responses.push(
          await request(app.app)
            .post('/api/registrations')
            .send(validExternalBody(token, { email: `a${attempt}@example.org` })),
        );
      }
      expect(responses.slice(0, 2).map((r) => r.status)).toEqual([201, 201]);
      expect(responses[2]?.status).toBe(429);
      expect(responses[2]?.body.error.code).toBe('RATE_LIMITED');
      expect(responses[2]?.headers['retry-after']).toBeDefined();
      // Rate-limited attempts are not stored.
      expect(app.repository.count()).toBe(2);
    } finally {
      app.dispose();
    }
  });

  it('does not apply the registration limit to the configuration endpoint', async () => {
    const app = createTestApplication({ env: { RATE_LIMIT_REGISTRATION_MAX: '1' } });
    try {
      for (let attempt = 0; attempt < 5; attempt += 1) {
        const response = await request(app.app).get('/api/registration-config?variant=external');
        expect(response.status).toBe(200);
      }
    } finally {
      app.dispose();
    }
  });

  it('lets many participants behind one shared address open the form (regression for F-07)', async () => {
    // One page load costs one configuration request. A limit tight enough to trip here
    // would deny the form to everyone behind a university or company NAT during a
    // registration rush, which is a worse failure than serving a public document twice.
    const app = createTestApplication();
    try {
      for (let attempt = 0; attempt < 120; attempt += 1) {
        const response = await request(app.app).get('/api/registration-config?variant=external');
        expect(response.status, `request ${attempt + 1} was throttled`).toBe(200);
      }
    } finally {
      app.dispose();
    }
  });

  it('still throttles the configuration endpoint once its own limit is reached', async () => {
    const app = createTestApplication({ env: { RATE_LIMIT_CONFIG_MAX: '3' } });
    try {
      const statuses: number[] = [];
      for (let attempt = 0; attempt < 5; attempt += 1) {
        statuses.push((await request(app.app).get('/api/registration-config?variant=external')).status);
      }
      expect(statuses).toEqual([200, 200, 200, 429, 429]);
    } finally {
      app.dispose();
    }
  });
});
