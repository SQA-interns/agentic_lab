/**
 * REST API / contract tests — GET /api/registration-config (specification § 4.2).
 *
 * Level justification: this endpoint is the contract between the browser and the
 * backend. Driving the real Express app over HTTP is the only level that checks the
 * status codes, the response shape and the header behaviour the frontend relies on.
 */
import request from 'supertest';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { createTestApplication, type TestApplication } from '../helpers/testApplication.js';

let application: TestApplication;

beforeEach(() => {
  application = createTestApplication();
});

afterEach(() => {
  application.dispose();
});

describe('GET /api/registration-config', () => {
  it('returns the fixed external fields in the documented order (AC-001-01)', async () => {
    const response = await request(application.app).get('/api/registration-config?variant=external');
    expect(response.status).toBe(200);
    expect(response.body.variant).toBe('external');
    expect(response.body.fields).toEqual(['firstName', 'lastName', 'email', 'organization']);
  });

  it('returns the fixed student fields in the documented order (AC-002-01)', async () => {
    const response = await request(application.app).get('/api/registration-config?variant=student');
    expect(response.body.fields).toEqual([
      'firstName',
      'lastName',
      'email',
      'studyInstitution',
      'studyProgramme',
      'studentId',
    ]);
  });

  it('publishes the same rule table the backend enforces (AC-G-03)', async () => {
    const response = await request(application.app).get('/api/registration-config?variant=student');
    expect(response.body.fieldRules.email).toMatchObject({ required: true, maxLength: 254, format: 'email' });
    expect(response.body.fieldRules.studentId).toMatchObject({ required: true, maxLength: 50, minLength: 2 });
    expect(typeof response.body.fieldRules.studentId.pattern).toBe('string');
  });

  it('offers the four option groups with only the active options (AC-001-02, AC-003-04)', async () => {
    const response = await request(application.app).get('/api/registration-config?variant=external');
    const groups = response.body.optionGroups as Array<{ id: string; options: Array<{ id: string }> }>;
    expect(groups.map((group) => group.id)).toEqual(['workshops', 'events', 'meals', 'other']);
    const allIds = groups.flatMap((group) => group.options.map((option) => option.id));
    expect(allIds).toContain('workshop-ai');
    expect(allIds).not.toContain('workshop-retired');
  });

  it('hides options that are not available to students (AC-002-02)', async () => {
    const response = await request(application.app).get('/api/registration-config?variant=student');
    const allIds = (response.body.optionGroups as Array<{ options: Array<{ id: string }> }>).flatMap((group) =>
      group.options.map((option) => option.id),
    );
    expect(allIds).not.toContain('workshop-external-only');
  });

  it('returns the mandatory consent without preselecting it (AC-G-08)', async () => {
    const response = await request(application.app).get('/api/registration-config?variant=external');
    expect(response.body.consents).toEqual([
      { id: 'privacy', required: true, text: 'I agree to the processing of my personal data.' },
    ]);
    // There is no "checked"/"default" property at all: the client has nothing to
    // pre-tick from.
    expect(Object.keys(response.body.consents[0])).toEqual(['id', 'required', 'text']);
  });

  it('issues a fresh form token on every request', async () => {
    const first = await request(application.app).get('/api/registration-config?variant=external');
    const second = await request(application.app).get('/api/registration-config?variant=external');
    expect(first.body.formToken).not.toBe(second.body.formToken);
    expect(first.body.formTokenTtlSeconds).toBe(1800);
  });

  it.each([['/api/registration-config'], ['/api/registration-config?variant=guest'], ['/api/registration-config?variant=']])(
    'rejects %s with a validation error',
    async (url) => {
      const response = await request(application.app).get(url);
      expect(response.status).toBe(400);
      expect(response.body.error.code).toBe('VALIDATION_ERROR');
      expect(response.body.error.fields[0].field).toBe('variant');
    },
  );

  it('carries a request id on every response', async () => {
    const response = await request(application.app).get('/api/registration-config?variant=external');
    expect(response.headers['x-request-id']).toMatch(/^[0-9a-f-]{36}$/u);
  });
});

describe('GET /api/health', () => {
  it('reports the service as available', async () => {
    const response = await request(application.app).get('/api/health');
    expect(response.status).toBe(200);
    expect(response.body.status).toBe('ok');
    expect(typeof response.body.uptimeSeconds).toBe('number');
  });
});

describe('unknown routes', () => {
  it('returns the documented 404 envelope, not an HTML error page', async () => {
    const response = await request(application.app).get('/api/does-not-exist');
    expect(response.status).toBe(404);
    expect(response.body.error.code).toBe('NOT_FOUND');
  });

  it('does not expose a participant list anywhere', async () => {
    for (const url of ['/api/registrations', '/api/registrations/all', '/api/participants']) {
      const response = await request(application.app).get(url);
      expect([404, 405]).toContain(response.status);
    }
  });
});
