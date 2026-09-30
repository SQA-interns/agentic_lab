import { afterEach, describe, expect, it, vi } from 'vitest';
import { fetchConfig, fetchOptions, submitRegistration, type RegistrationRequest } from './api';

const REQUEST: RegistrationRequest = {
  type: 'EXTERNAL',
  firstName: 'Ana',
  lastName: 'Novak',
  email: 'a@x.si',
  organization: 'IJS',
  optionIds: [],
  consentIds: ['dp'],
  captchaToken: 't',
};

function respond(status: number, body: string, contentType = 'application/json') {
  const fetchMock = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
    async () => new Response(body, { status, headers: { 'Content-Type': contentType } }),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('api', () => {
  it('loads configuration and options from /api', async () => {
    const fetchMock = respond(200, '{"conferenceName":"K"}');
    expect((await fetchConfig()).conferenceName).toBe('K');
    await fetchOptions('STUDENT');
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual([
      '/api/config',
      '/api/options?type=STUDENT',
    ]);
  });

  it('rejects when loading fails', async () => {
    respond(503, '');
    await expect(fetchConfig()).rejects.toThrow('503');
  });

  it('posts JSON and returns the confirmation on 201', async () => {
    const fetchMock = respond(201, '{"reference":"r1"}');
    const result = await submitRegistration(REQUEST);
    expect(result).toEqual({ ok: true, confirmation: { reference: 'r1' } });
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/registrations');
    expect(init?.method).toBe('POST');
    expect(JSON.parse(String(init?.body))).toEqual(REQUEST);
  });

  it('returns field errors from a 400', async () => {
    respond(400, '{"message":"Fix it","errors":[{"field":"email","message":"bad"}]}');
    expect(await submitRegistration(REQUEST)).toEqual({
      ok: false,
      status: 400,
      error: { message: 'Fix it', errors: [{ field: 'email', message: 'bad' }] },
    });
  });

  it('falls back to a generic message for unusable error bodies', async () => {
    respond(502, '<html>bad gateway</html>', 'text/html');
    const html = await submitRegistration(REQUEST);
    expect(html.ok).toBe(false);
    if (!html.ok) {
      expect(html.error.message).toMatch(/could not be sent/);
      expect(html.error.errors).toEqual([]);
    }
    respond(429, '{"message":"","errors":"nope"}');
    const odd = await submitRegistration(REQUEST);
    if (!odd.ok) {
      expect(odd.status).toBe(429);
      expect(odd.error.message).toMatch(/could not be sent/);
      expect(odd.error.errors).toEqual([]);
    }
  });

  it('reports a network failure as status 0', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('offline');
      }),
    );
    const result = await submitRegistration(REQUEST);
    expect(result).toMatchObject({ ok: false, status: 0 });
  });
});
