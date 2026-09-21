/**
 * Contract tests — the frontend's interpretation of backend responses
 * (specification § 4.3, § 9.3).
 *
 * Level justification: AC-004-01, AC-004-04 and AC-004-05 all depend on the client
 * telling success, validation rejection and technical failure apart. These tests feed the
 * client each documented response and assert the branch it chooses, which is the decision
 * the confirmation rule rests on.
 */
import { afterEach, describe, expect, it, vi } from 'vitest';

import { submitRegistration, type RegistrationRequest } from '../src/api.js';

const request: RegistrationRequest = {
  variant: 'external',
  firstName: 'Ana',
  lastName: 'Novak',
  email: 'ana@example.org',
  organization: 'UM',
  selectedOptionIds: [],
  consents: { privacy: true },
  formToken: 'token',
  website: '',
};

function respondWith(status: number, body: unknown): void {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      status,
      ok: status >= 200 && status < 300,
      json: () => Promise.resolve(body),
    }),
  );
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('submitRegistration (AC-G-02: each documented status maps to its own outcome)', () => {
  it('treats 201 as a created registration — the only path to a confirmation (AC-004-01)', async () => {
    respondWith(201, { reference: 'REG-20260921-ABCDEFGHJK', email: 'ana@example.org' });
    const outcome = await submitRegistration(request);
    expect(outcome.kind).toBe('created');
  });

  it('treats 400 VALIDATION_ERROR as a validation outcome carrying the field list (AC-004-04)', async () => {
    respondWith(400, {
      error: {
        code: 'VALIDATION_ERROR',
        message: 'Some fields are invalid.',
        fields: [{ field: 'email', code: 'invalid_email', message: 'Enter a valid email address.' }],
      },
    });
    const outcome = await submitRegistration(request);
    expect(outcome).toMatchObject({ kind: 'validation' });
    if (outcome.kind === 'validation') {
      expect(outcome.fields[0]?.field).toBe('email');
    }
  });

  it('treats 400 ANTI_AUTOMATION_FAILED as its own outcome, not as a field problem', async () => {
    respondWith(400, { error: { code: 'ANTI_AUTOMATION_FAILED', message: 'Please reload the form.' } });
    const outcome = await submitRegistration(request);
    expect(outcome.kind).toBe('anti-automation');
  });

  it('treats 429 as rate limiting', async () => {
    respondWith(429, { error: { code: 'RATE_LIMITED', message: 'Too many attempts.' } });
    const outcome = await submitRegistration(request);
    expect(outcome).toMatchObject({ kind: 'rate-limited', message: 'Too many attempts.' });
  });

  it.each([[500], [502], [503]])('treats %d as a technical failure, never a validation failure (AC-004-05)', async (status) => {
    respondWith(status, { error: { code: 'INTERNAL_ERROR', message: 'x' } });
    const outcome = await submitRegistration(request);
    expect(outcome.kind).toBe('technical');
  });

  it('treats a network failure as a technical failure and says the data was kept (AC-004-05)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    const outcome = await submitRegistration(request);
    expect(outcome.kind).toBe('technical');
    expect(outcome.kind === 'technical' && outcome.message).toContain('kept');
  });

  it('treats an unparseable error body as a technical failure rather than crashing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        status: 400,
        ok: false,
        json: () => Promise.reject(new Error('not json')),
      }),
    );
    const outcome = await submitRegistration(request);
    expect(outcome.kind).toBe('technical');
  });

  it('never reports success for a non-201 response', async () => {
    for (const status of [200, 202, 204, 301, 400, 401, 403, 429, 500]) {
      respondWith(status, { error: { code: 'X', message: 'x' } });
      expect((await submitRegistration(request)).kind).not.toBe('created');
    }
  });

  it('sends the registration as JSON to the registrations endpoint', async () => {
    const fetchMock = vi.fn().mockResolvedValue({ status: 201, ok: true, json: () => Promise.resolve({}) });
    vi.stubGlobal('fetch', fetchMock);
    await submitRegistration(request);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toContain('/registrations');
    expect(init.method).toBe('POST');
    expect((init.headers as Record<string, string>)['Content-Type']).toBe('application/json');
    expect(JSON.parse(init.body as string)).toEqual(request);
  });
});
