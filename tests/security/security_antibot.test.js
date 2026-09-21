const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { initDatabase, closeDatabase } = require('../../src/db/database');
const createApp = require('../../src/app');
const { MemoryRateLimiter } = require('../../src/middleware/rateLimiter');

describe('Security & Anti-Bot Controls Tests', () => {
  let server;
  let baseUrl;

  before(async () => {
    initDatabase(':memory:');
    const app = createApp();
    server = http.createServer(app);
    await new Promise((resolve) => {
      server.listen(0, '127.0.0.1', () => {
        const addr = server.address();
        baseUrl = `http://127.0.0.1:${addr.port}`;
        resolve();
      });
    });
  });

  after(async () => {
    await new Promise((resolve) => server.close(resolve));
    closeDatabase();
  });

  test('Anti-bot honeypot rejects automated bot submission with 400 Bad Request', async () => {
    const botPayload = {
      registrationType: 'external',
      firstName: 'BotFirst',
      lastName: 'BotLast',
      email: 'bot@spam.com',
      organization: 'SpamCorp',
      selectedOptions: ['ws-cloud-native'],
      privacyConsent: true,
      website_hp: 'http://spam-link.example.com' // Filled honeypot
    };

    const res = await fetch(`${baseUrl}/api/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(botPayload)
    });

    assert.equal(res.status, 400);
    const body = await res.json();
    assert.equal(body.success, false);
    assert.ok(body.error.includes('Automated submission') || (body.details && body.details.some(d => d.field === 'honeypot')));
  });

  test('HTTP Security Headers are present on responses', async () => {
    const res = await fetch(`${baseUrl}/api/health`);
    assert.equal(res.headers.get('x-content-type-options'), 'nosniff');
    assert.equal(res.headers.get('x-frame-options'), 'DENY');
    assert.ok(res.headers.get('content-security-policy'));
  });

  test('Rate limiter blocks excessive rapid registration requests with 429 Too Many Requests', async () => {
    // Custom tight rate limiter test instance
    const tightLimiter = new MemoryRateLimiter(60000, 3);
    const mockReq = { ip: '192.168.1.100', headers: {} };
    let status = null;
    let jsonBody = null;
    const mockRes = {
      setHeader: () => {},
      status: (code) => {
        status = code;
        return {
          json: (b) => { jsonBody = b; }
        };
      }
    };
    let nextCalled = 0;
    const next = () => { nextCalled++; };

    const middleware = tightLimiter.middleware();

    // First 3 requests succeed
    middleware(mockReq, mockRes, next);
    middleware(mockReq, mockRes, next);
    middleware(mockReq, mockRes, next);
    assert.equal(nextCalled, 3);
    assert.equal(status, null);

    // 4th request should be blocked
    middleware(mockReq, mockRes, next);
    assert.equal(status, 429);
    assert.equal(jsonBody.success, false);
    assert.ok(jsonBody.error.includes('Too many registration attempts'));
  });

  test('Input text containing HTML/script tags is sanitized before storage', async () => {
    const xssPayload = {
      registrationType: 'external',
      firstName: '<script>alert(1)</script>Marko',
      lastName: 'Novak<img src=x onerror=alert(2)>',
      email: 'marko.xss@test.si',
      organization: '<b>Inštitut</b> Jožef Stefan',
      selectedOptions: [],
      privacyConsent: true
    };

    const res = await fetch(`${baseUrl}/api/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(xssPayload)
    });

    assert.equal(res.status, 201);
    const body = await res.json();
    assert.equal(body.data.firstName, 'Marko');
    assert.equal(body.data.lastName, 'Novak');
    assert.equal(body.data.organization, 'Inštitut Jožef Stefan');
  });
});
