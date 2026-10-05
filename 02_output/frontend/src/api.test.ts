import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchFormConfig, submitRegistration, type RegistrationBody } from './api';

const body: RegistrationBody = {
  type: 'EXTERNAL',
  optionIds: [],
  consentGiven: true,
  captchaToken: 't',
};

function respond(status: number, payload: unknown) {
  const fetchMock = vi
    .fn()
    .mockResolvedValue(
      new Response(typeof payload === 'string' ? payload : JSON.stringify(payload), { status }),
    );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('fetchFormConfig', () => {
  it('reads the relative endpoint', async () => {
    const fetchMock = respond(200, { conferenceName: 'K' });
    await expect(fetchFormConfig()).resolves.toEqual({ conferenceName: 'K' });
    expect(fetchMock).toHaveBeenCalledWith('/api/form-config', expect.anything());
  });

  it('throws on an error status', async () => {
    respond(500, {});
    await expect(fetchFormConfig()).rejects.toThrow('form configuration: 500');
  });
});

describe('submitRegistration', () => {
  it('posts JSON and returns the accepted registration', async () => {
    const fetchMock = respond(201, { id: 'i', type: 'EXTERNAL', acceptedAt: 'a' });
    await expect(submitRegistration(body)).resolves.toEqual({
      kind: 'accepted',
      id: 'i',
      acceptedAt: 'a',
    });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/registrations');
    expect(init.method).toBe('POST');
    expect(init.headers).toEqual({
      'Content-Type': 'application/json',
      Accept: 'application/json',
    });
    expect(JSON.parse(init.body as string)).toEqual(body);
  });

  it('returns field errors of a validation problem', async () => {
    const errors = [{ field: 'email', code: 'INVALID_EMAIL' }];
    respond(400, { code: 'VALIDATION_FAILED', errors });
    await expect(submitRegistration(body)).resolves.toEqual({ kind: 'invalid', errors });
  });

  it.each([
    [409, 'EMAIL_ALREADY_REGISTERED', 'EMAIL_ALREADY_REGISTERED'],
    [429, 'RATE_LIMITED', 'RATE_LIMITED'],
    [503, 'STORAGE_UNAVAILABLE', 'STORAGE_UNAVAILABLE'],
    [400, 'MALFORMED_REQUEST', 'GENERIC'],
    [500, 'INTERNAL_ERROR', 'GENERIC'],
  ])('maps status %i code %s to %s', async (status, code, expected) => {
    respond(status, { code });
    await expect(submitRegistration(body)).resolves.toEqual({ kind: 'failed', code: expected });
  });

  it.each([
    [200, { id: 'i', acceptedAt: 'a' }],
    [201, { acceptedAt: 'a' }],
    [201, { id: 'i' }],
    [400, { code: 'VALIDATION_FAILED' }],
    [400, { code: 'MALFORMED_REQUEST', errors: [{ field: 'x', code: 'y' }] }],
    [422, { code: 'VALIDATION_FAILED', errors: [{ field: 'x', code: 'y' }] }],
  ])('does not trust an incomplete answer (status %i)', async (status, payload) => {
    respond(status, payload);
    await expect(submitRegistration(body)).resolves.toEqual({ kind: 'failed', code: 'GENERIC' });
  });

  it('treats an unreadable body or a network error as generic', async () => {
    respond(502, '<html>');
    await expect(submitRegistration(body)).resolves.toEqual({ kind: 'failed', code: 'GENERIC' });
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')));
    await expect(submitRegistration(body)).resolves.toEqual({ kind: 'failed', code: 'GENERIC' });
  });
});
