import { describe, expect, it, vi } from 'vitest';
import { ApiError, fetchFormToken, submitRegistration } from './client';

describe('api client', () => {
  it('maps the backend error body to ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          new Response(
            JSON.stringify({
              status: 400,
              error: 'VALIDATION_FAILED',
              message: 'Fix it',
              fieldErrors: { email: 'bad' },
            }),
            { status: 400 },
          ),
        ),
      ),
    );
    const error = await submitRegistration('EXTERNAL', {}).catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'Fix it',
      fieldErrors: { email: 'bad' },
    });
  });

  it('handles non-JSON error responses', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(new Response('<html>Bad gateway</html>', { status: 502 }))),
    );
    await expect(fetchFormToken()).rejects.toMatchObject({ status: 502, code: 'HTTP_502' });
  });

  it('ignores malformed fieldErrors', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          new Response(JSON.stringify({ error: 'X', fieldErrors: { a: 1 } }), { status: 400 }),
        ),
      ),
    );
    await expect(fetchFormToken()).rejects.toMatchObject({ fieldErrors: {} });
  });

  it('sends JSON with the right headers', async () => {
    const fetchMock = vi.fn(() =>
      Promise.resolve(new Response(JSON.stringify({ id: '1' }), { status: 201 })),
    );
    vi.stubGlobal('fetch', fetchMock);
    await submitRegistration('STUDENT', { firstName: 'Ana' });
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/registrations/student');
    const headers = new Headers(init.headers);
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(headers.get('Accept')).toBe('application/json');
    expect(init.body).toBe('{"firstName":"Ana"}');
  });
});
