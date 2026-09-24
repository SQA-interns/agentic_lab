#!/usr/bin/env node
// Container smoke test (DEFINITION_OF_DONE §4): exercises the RUNNING docker-compose stack
// through the Nginx frontend container, exactly as a browser would.
//
//   docker compose -f docker-compose.yml -f docker-compose.verify.yml up -d --build
//   ADMIN_USERNAME=... ADMIN_PASSWORD=... node scripts/smoke-test.mjs
//
// Optional: BASE_URL (default http://localhost:8080), OPS_URL (http://127.0.0.1:8081),
// MAILPIT_URL (http://127.0.0.1:8025), SKIP_RATE_LIMIT=1.
import { execSync } from 'node:child_process';

const BASE = process.env.BASE_URL ?? 'http://localhost:8080';
const OPS = process.env.OPS_URL ?? 'http://127.0.0.1:8081';
const MAILPIT = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025';
const ADMIN = `${process.env.ADMIN_USERNAME ?? ''}:${process.env.ADMIN_PASSWORD ?? ''}`;

const results = [];
function check(name, ok, detail = '') {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  — ' + detail : ''}`);
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const FRONTEND_HEADERS = {
  'content-security-policy': /default-src 'self'.*frame-ancestors 'none'/,
  'x-content-type-options': /^nosniff$/,
  'x-frame-options': /^DENY$/,
  'referrer-policy': /strict-origin-when-cross-origin/,
  'permissions-policy': /camera=\(\)/,
};
const API_HEADERS = {
  'content-security-policy': /^default-src 'none'; frame-ancestors 'none'$/,
  'x-content-type-options': /^nosniff$/,
  'x-frame-options': /^DENY$/,
  'referrer-policy': /^no-referrer$/,
};
function checkHeaders(label, res, expected) {
  const missing = Object.entries(expected)
    .filter(([h, re]) => !re.test(res.headers.get(h) ?? ''))
    .map(([h]) => `${h}=${res.headers.get(h)}`);
  check(`${label}: security headers`, missing.length === 0, missing.join(', '));
  const dupCsp = (res.headers.get('content-security-policy') ?? '').includes(',');
  check(`${label}: single CSP header`, !dupCsp);
}

async function token() {
  const res = await fetch(`${BASE}/api/form-token`);
  return (await res.json()).token;
}
async function post(path, body, headers = {}) {
  return fetch(`${BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...headers },
    body: JSON.stringify(body),
  });
}

async function frontend() {
  const root = await fetch(`${BASE}/`);
  const html = await root.text();
  check('GET / serves the SPA', root.status === 200 && html.includes('<div id="root">'));
  checkHeaders('GET /', root, FRONTEND_HEADERS);
  check('server version hidden', !/\d/.test(root.headers.get('server') ?? ''), root.headers.get('server') ?? '');
  for (const path of ['/register/external', '/register/student']) {
    const res = await fetch(`${BASE}${path}`);
    check(`GET ${path} (SPA fallback)`, res.status === 200);
    checkHeaders(`GET ${path}`, res, FRONTEND_HEADERS);
  }
  const asset = html.match(/src="(\/assets\/[^"]+\.js)"/)?.[1];
  const assetRes = await fetch(`${BASE}${asset}`);
  check('GET JS asset', assetRes.status === 200, asset);
  checkHeaders('GET asset', assetRes, FRONTEND_HEADERS);
}

async function health() {
  for (const path of ['/actuator/health', '/actuator/health/liveness', '/actuator/health/readiness']) {
    const res = await fetch(`${OPS}${path}`);
    const body = await res.json().catch(() => ({}));
    check(`GET ${path}`, res.status === 200 && body.status === 'UP', JSON.stringify(body));
  }
  const env = await fetch(`${OPS}/actuator/env`);
  check('actuator/env not exposed', env.status === 401 || env.status === 404, String(env.status));
}

async function options() {
  const ext = await fetch(`${BASE}/api/options?type=EXTERNAL`);
  const extBody = await ext.json();
  check('GET /api/options EXTERNAL', ext.status === 200 && extBody.length > 0, `${extBody.length} options`);
  checkHeaders('GET /api/options', ext, API_HEADERS);
  const ids = extBody.map((o) => o.id);
  check('inactive option not offered', !ids.includes('ws-legacy'));
  const stu = await (await fetch(`${BASE}/api/options?type=STUDENT`)).json();
  check('student-only option only for students', stu.some((o) => o.id === 'ev-career-fair') && !ids.includes('ev-career-fair'));
}

async function registrations() {
  const tExt = await token();
  const tStu = await token();
  const fast = await post('/api/registrations/external', {
    firstName: 'Bot', lastName: 'Fast', email: 'bot@example.com', organization: 'Bots',
    privacyConsent: true, optionIds: [], formToken: await token(), website: '',
  });
  check('too-fast submission rejected', fast.status === 400 && (await fast.json()).error === 'SUBMISSION_REJECTED');

  await sleep(3200);
  const ext = await post('/api/registrations/external', {
    firstName: 'Žiga', lastName: 'Čeh', email: 'ziga.ceh@example.si',
    organization: 'Univerza v Mariboru – FERI', privacyConsent: true,
    optionIds: ['ws-secure-coding', 'ev-gala-dinner', 'meal-lunch-day1'], formToken: tExt, website: '',
  });
  const extBody = await ext.json();
  check('external registration → 201', ext.status === 201 && extBody.type === 'EXTERNAL', extBody.id);
  checkHeaders('POST external', ext, API_HEADERS);

  const stu = await post('/api/registrations/student', {
    firstName: 'Špela', lastName: 'Kovač', email: 'spela.kovac@student.um.si',
    studyInstitution: 'FERI Maribor', studyProgramme: 'Računalništvo in informacijske tehnologije',
    studentId: 'E1234567', privacyConsent: true, optionIds: ['ev-career-fair', 'act-city-tour'],
    formToken: tStu, website: '',
  });
  const stuBody = await stu.json();
  check('student registration → 201', stu.status === 201 && stuBody.type === 'STUDENT', stuBody.id);

  const replay = await post('/api/registrations/student', {
    firstName: 'Špela', lastName: 'Kovač', email: 'spela.kovac@student.um.si',
    studyInstitution: 'FERI Maribor', studyProgramme: 'RIT', studentId: 'E1234567',
    privacyConsent: true, optionIds: [], formToken: tStu, website: '',
  });
  check('form token replay rejected', replay.status === 400);

  const invalid = await post('/api/registrations/external', {
    firstName: 'Ana', lastName: 'Novak', email: 'not-an-email', organization: 'X',
    privacyConsent: false, optionIds: ['ws-legacy'], formToken: await token(), website: '',
  });
  const invalidBody = await invalid.json();
  check('invalid registration → 400 with field errors',
    invalid.status === 400 && invalidBody.fieldErrors?.email !== undefined, JSON.stringify(invalidBody.fieldErrors));

  const malformed = await fetch(`${BASE}/api/registrations/external`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{"firstName":',
  });
  const malformedText = await malformed.text();
  check('malformed JSON → 400 without internals', malformed.status === 400 && !/Exception|at org\./.test(malformedText));

  const big = await fetch(`${BASE}/api/registrations/external`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: `{"x":"${'a'.repeat(20000)}"}`,
  });
  check('oversized body → 413', big.status === 413, String(big.status));

  return { extId: extBody.id, stuId: stuBody.id };
}

async function backups(ids) {
  const listing = execSync('docker compose exec -T backend ls -l /data/backups', { encoding: 'utf8' });
  for (const id of Object.values(ids)) {
    check(`backup file for ${id}`, listing.includes(id));
  }
  check('backup files are 0600', listing.split('\n').filter((l) => l.includes('.json')).every((l) => l.startsWith('-rw-------')));
  const content = execSync(`docker compose exec -T backend sh -c "cat /data/backups/*${ids.extId}.json"`, { encoding: 'utf8' });
  const json = JSON.parse(content);
  check('backup JSON content', json.participant.firstName === 'Žiga' && json.options.length === 3 && json.privacyConsent === true);
}

async function mails(ids) {
  let messages = [];
  for (let i = 0; i < 50; i++) {
    messages = (await (await fetch(`${MAILPIT}/api/v1/messages?limit=200`)).json()).messages ?? [];
    const details = await Promise.all(
      messages.map(async (m) => (await fetch(`${MAILPIT}/api/v1/message/${m.ID}`)).json()),
    );
    const forId = (id) => details.filter((d) => (d.Text ?? '').includes(id));
    if (forId(ids.extId).length >= 2 && forId(ids.stuId).length >= 2) {
      for (const [label, id, participant] of [
        ['external', ids.extId, 'ziga.ceh@example.si'],
        ['student', ids.stuId, 'spela.kovac@student.um.si'],
      ]) {
        const mine = forId(id);
        const conf = mine.find((d) => d.To.some((t) => t.Address === participant));
        const org = mine.find((d) => d.To.some((t) => t.Address === 'organizers@conference.test'));
        check(`${label}: participant confirmation email`, conf !== undefined && conf.Subject.includes('Registration confirmed –'), conf?.Subject);
        check(`${label}: confirmation is UTF-8 plain text`, conf !== undefined && (conf.Text.includes('Žiga Čeh') || conf.Text.includes('Špela Kovač')) && (conf.HTML ?? '') === '');
        check(`${label}: organizer notification to all organizers`, org !== undefined && org.To.length === 2);
        const att = org?.Attachments?.find((a) => a.FileName === `registration-${id}.json`);
        check(`${label}: organizer JSON attachment`, att !== undefined && att.ContentType === 'application/json');
      }
      return;
    }
    await sleep(200);
  }
  check('emails delivered to Mailpit', false, `${messages.length} messages`);
}

async function exportXlsx(ids) {
  const anon = await fetch(`${BASE}/api/admin/registrations/export`);
  check('export without credentials → 401', anon.status === 401 && anon.headers.get('www-authenticate') !== null);
  const wrong = await fetch(`${BASE}/api/admin/registrations/export`, {
    headers: { Authorization: 'Basic ' + Buffer.from('organizer:wrong-password-x').toString('base64') },
  });
  check('export with wrong password → 401', wrong.status === 401);
  const ok = await fetch(`${BASE}/api/admin/registrations/export`, {
    headers: { Authorization: 'Basic ' + Buffer.from(ADMIN).toString('base64') },
  });
  const buf = Buffer.from(await ok.arrayBuffer());
  check('export with credentials → xlsx',
    ok.status === 200 &&
      ok.headers.get('content-type') === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' &&
      buf.subarray(0, 2).toString() === 'PK',
    `${buf.length} bytes, ${ok.headers.get('content-disposition')}`);
  return buf.length > 0 && ids !== undefined;
}

async function rateLimit() {
  // Spoofed X-Forwarded-For must not bypass the per-IP limit: Nginx overwrites the header.
  let got429;
  for (let i = 0; i < 15 && !got429; i++) {
    const res = await post('/api/registrations/external', { firstName: 'x' }, {
      'X-Forwarded-For': `203.0.113.${i}`,
    });
    if (res.status === 429) got429 = res;
  }
  check('rate limit enforced through proxy despite spoofed X-Forwarded-For', got429 !== undefined);
  if (got429) {
    check('429 has Retry-After', got429.headers.get('retry-after') !== null);
    checkHeaders('429 response', got429, API_HEADERS);
  }
}

const started = new Date().toISOString();
await frontend();
await health();
await options();
const ids = await registrations();
await backups(ids);
await mails(ids);
await exportXlsx(ids);
if (!process.env.SKIP_RATE_LIMIT) {
  await rateLimit();
}
const failed = results.filter((r) => !r.ok);
console.log(`\n${started} → ${new Date().toISOString()}: ${results.length - failed.length}/${results.length} checks passed`);
console.log(`registrations: external=${ids.extId} student=${ids.stuId}`);
process.exit(failed.length === 0 ? 0 : 1);
