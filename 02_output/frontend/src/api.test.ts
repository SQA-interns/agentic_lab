import { fetchCatalog, submitRegistration } from './api';
import { catalog, jsonResponse } from './test/fixtures';

describe('api', () => {
  it('loads the catalog', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(200, catalog));
    await expect(fetchCatalog(fetchImpl)).resolves.toEqual(catalog);
    expect(fetchImpl).toHaveBeenCalledWith('/api/catalog', expect.anything());
  });

  it('fails when the catalog is unavailable', async () => {
    await expect(fetchCatalog(vi.fn().mockResolvedValue(jsonResponse(503, {})))).rejects.toThrow(
      '503',
    );
  });

  it('posts JSON and maps acceptance and replay', async () => {
    const accepted = {
      registrationId: 'r',
      clientRequestId: 'c',
      formType: 'external',
      status: 'ACCEPTED',
      acceptedAt: 't',
    };
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(201, accepted));
    await expect(submitRegistration('external', { a: 1 }, fetchImpl)).resolves.toEqual({
      kind: 'accepted',
      accepted,
    });
    const [url, init] = fetchImpl.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/registrations/external');
    expect(init.method).toBe('POST');
    expect(init.body).toBe('{"a":1}');
    const replay = await submitRegistration(
      'student',
      {},
      vi.fn().mockResolvedValue(jsonResponse(200, accepted)),
    );
    expect(replay.kind).toBe('accepted');
  });

  it.each([
    [400, { errors: [{ field: 'email', code: 'INVALID_EMAIL', message: 'm' }] }, 'invalid'],
    [409, {}, 'conflict'],
    [429, {}, 'rate-limited'],
    [503, {}, 'not-saved'],
    [500, {}, 'not-saved'],
  ])('maps status %i', async (status, body, kind) => {
    const result = await submitRegistration(
      'external',
      {},
      vi.fn().mockResolvedValue(jsonResponse(status, body)),
    );
    expect(result.kind).toBe(kind);
  });

  it('treats network errors and unreadable bodies as not saved', async () => {
    expect(
      (await submitRegistration('external', {}, vi.fn().mockRejectedValue(new TypeError('x'))))
        .kind,
    ).toBe('not-saved');
    const garbage = new Response('<html>', { status: 201 });
    expect(
      (await submitRegistration('external', {}, vi.fn().mockResolvedValue(garbage))).kind,
    ).toBe('not-saved');
    const garbage400 = new Response('<html>', { status: 400 });
    expect(
      (await submitRegistration('external', {}, vi.fn().mockResolvedValue(garbage400))).kind,
    ).toBe('not-saved');
    const noErrors = await submitRegistration(
      'external',
      {},
      vi.fn().mockResolvedValue(jsonResponse(400, {})),
    );
    expect(noErrors).toEqual({ kind: 'invalid', errors: [] });
  });
});
