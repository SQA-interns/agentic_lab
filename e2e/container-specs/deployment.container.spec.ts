/**
 * Container deployment tests (specification § 11, § 13; AC-G-11, AC-G-14).
 *
 * Level justification: these assert on properties of the *shipped artefact* — the nginx
 * image serving the built documents and proxying the API — which no in-process test can
 * observe. They run only against a running compose stack, via
 * `playwright.container.config.ts`.
 *
 * The security-header tests are also the regression test for verification finding F-06:
 * nginx's `add_header` is not additive across levels, so a location block that sets a
 * Cache-Control header silently discards every header inherited from the server block.
 * That defect left the HTML documents — the things the policy exists to protect — with no
 * Content-Security-Policy at all, while `curl` against the API still looked correct.
 */
import { expect, test } from '@playwright/test';

const DOCUMENT_PATHS = ['/', '/studentska-prijava/'];

const REQUIRED_DOCUMENT_HEADERS: ReadonlyArray<[string, RegExp]> = [
  ['content-security-policy', /default-src 'self'/u],
  ['x-content-type-options', /^nosniff$/u],
  ['x-frame-options', /^DENY$/u],
  ['referrer-policy', /^no-referrer$/u],
  ['permissions-policy', /camera=\(\)/u],
];

test.describe('document security headers (AC-G-11, regression for F-06)', () => {
  for (const path of DOCUMENT_PATHS) {
    test(`every required header is present on ${path}`, async ({ request }) => {
      const response = await request.get(path);
      expect(response.status()).toBe(200);

      const headers = response.headers();
      for (const [name, pattern] of REQUIRED_DOCUMENT_HEADERS) {
        expect(headers[name], `${name} missing on ${path}`).toBeDefined();
        expect(headers[name]).toMatch(pattern);
      }
    });
  }

  test('a not-found response still carries the headers', async ({ request }) => {
    // `try_files ... =404` is served from the same location block, so this is where the
    // `always` flag on each add_header matters.
    const response = await request.get('/does-not-exist.txt');
    expect(response.status()).toBe(404);

    const headers = response.headers();
    for (const [name] of REQUIRED_DOCUMENT_HEADERS) {
      expect(headers[name], `${name} missing on a 404`).toBeDefined();
    }
  });

  test('hashed assets are cached immutably and still carry the headers', async ({ request, baseURL }) => {
    const page = await (await request.get('/')).text();
    const asset = /\/assets\/[A-Za-z0-9._-]+\.js/u.exec(page)?.[0];
    expect(asset, 'no hashed asset referenced by the page').toBeDefined();

    const response = await request.get(new URL(asset as string, baseURL).toString());
    expect(response.status()).toBe(200);
    expect(response.headers()['cache-control']).toBe('public, max-age=31536000, immutable');
    expect(response.headers()['x-content-type-options']).toBe('nosniff');
  });

  test('the server version is not advertised', async ({ request }) => {
    const server = (await request.get('/')).headers().server;
    expect(server).toBeDefined();
    expect(server).not.toMatch(/\d/u);
  });
});

test.describe('API through the nginx proxy (AC-G-01, AC-G-14)', () => {
  test('the API is reachable same-origin and is healthy', async ({ request }) => {
    const response = await request.get('/api/health');
    expect(response.status()).toBe(200);
    expect((await response.json()).status).toBe('ok');
  });

  test('the API carries its own policy, not the document policy', async ({ request }) => {
    // Two Content-Security-Policy headers would mean the document policy is being applied
    // to API responses as well; browsers then intersect them, which is not what either
    // policy was written for.
    const response = await request.get('/api/health');
    const csp = response.headers()['content-security-policy'];
    expect(csp).toContain("default-src 'none'");
    expect(csp).not.toContain("default-src 'self'");
  });

  test('the backend security headers survive the proxy', async ({ request }) => {
    const headers = (await request.get('/api/health')).headers();
    expect(headers['x-content-type-options']).toBe('nosniff');
    expect(headers['x-frame-options']).toBe('DENY');
    expect(headers['x-powered-by']).toBeUndefined();
  });

  test('the export is not reachable without credentials', async ({ request }) => {
    const response = await request.get('/api/export/registrations.xlsx');
    expect(response.status()).toBe(401);
    expect(response.headers()['www-authenticate']).toContain('Basic');
  });
});
