import { expect, test } from '@playwright/test';
import { uniqueEmail } from './support/env';
import { schemaErrors } from './support/contract';

// Live responses through the proxy must match docs/02_contracts/openapi.yaml (DoD-P04).
test.describe('REST contract conformance', () => {
  test('AC-003-01 catalog response matches the Catalog schema and hides inactive options', async ({
    request,
  }) => {
    const response = await request.get('/api/catalog');
    expect(response.status()).toBe(200);
    const body: unknown = await response.json();
    expect(await schemaErrors('Catalog', body)).toEqual([]);
    expect(JSON.stringify(body)).not.toContain('ws-retired');
  });

  test('AC-001-01 AC-004-03 accepted and replayed responses match RegistrationAccepted', async ({
    request,
  }) => {
    const payload = {
      clientRequestId: crypto.randomUUID(),
      captchaToken: 'local-captcha-ok',
      firstName: 'Contract',
      lastName: 'Check',
      email: uniqueEmail('e2e-contract'),
      organization: 'Synthetic Org',
      selections: { workshops: ['ws-testing'], events: [], meals: [], other: [] },
      consentGiven: true,
    };
    const created = await request.post('/api/registrations/external', { data: payload });
    expect(created.status()).toBe(201);
    const body: unknown = await created.json();
    expect(await schemaErrors('RegistrationAccepted', body)).toEqual([]);

    const replay = await request.post('/api/registrations/external', { data: payload });
    expect(replay.status()).toBe(200);
    expect(((await replay.json()) as { registrationId: string }).registrationId).toBe(
      (body as { registrationId: string }).registrationId,
    );
  });

  test('AC-002-02 validation failure matches ValidationProblem', async ({ request }) => {
    const response = await request.post('/api/registrations/student', {
      data: {
        clientRequestId: crypto.randomUUID(),
        captchaToken: 'local-captcha-ok',
        firstName: ' ',
        lastName: 'X',
        email: uniqueEmail('e2e-contract'),
        studyInstitution: 'U',
        studyProgramme: 'P',
        selections: {},
        consentGiven: true,
      },
    });
    expect(response.status()).toBe(400);
    expect(response.headers()['content-type']).toContain('application/problem+json');
    const body = (await response.json()) as { errors: { field: string; code: string }[] };
    expect(await schemaErrors('ValidationProblem', body)).toEqual([]);
    expect(body.errors).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ field: 'firstName', code: 'REQUIRED' }),
        expect.objectContaining({ field: 'studentId', code: 'REQUIRED' }),
      ]),
    );
  });
});
