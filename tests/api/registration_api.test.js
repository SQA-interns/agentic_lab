const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const { initDatabase, closeDatabase } = require('../../src/db/database');
const createApp = require('../../src/app');

describe('REST API & Contract Tests', () => {
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

  test('GET /api/health returns 200 OK with health status', async () => {
    const res = await fetch(`${baseUrl}/api/health`);
    assert.equal(res.status, 200);
    const data = await res.json();
    assert.equal(data.status, 'ok');
    assert.ok(typeof data.uptime === 'number');
  });

  test('GET /api/conference-options returns 200 OK with active options', async () => {
    const res = await fetch(`${baseUrl}/api/conference-options`);
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.success, true);
    assert.ok(body.data.workshops);
    assert.ok(body.data.workshops.some(w => w.id === 'ws-cloud-native'));
    // Inactive option must not be returned
    assert.ok(!body.data.workshops.some(w => w.id === 'ws-legacy-migration'));
  });

  test('POST /api/register successfully creates external registration (Happy Path)', async () => {
    const payload = {
      registrationType: 'external',
      firstName: 'Ana',
      lastName: 'Novak',
      email: 'ana.novak@institut.si',
      organization: 'Inštitut Jožef Stefan',
      selectedOptions: ['ws-cloud-native', 'meal-vegetarian'],
      privacyConsent: true,
      website_hp: ''
    };

    const res = await fetch(`${baseUrl}/api/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    assert.equal(res.status, 201, 'Status code must be 201 Created');
    const body = await res.json();
    assert.equal(body.success, true);
    assert.ok(body.data.registrationId.startsWith('reg_'));
    assert.equal(body.data.firstName, 'Ana');
    assert.equal(body.data.email, 'ana.novak@institut.si');
    assert.equal(body.data.organization, 'Inštitut Jožef Stefan');
    assert.equal(body.data.selectedOptions.length, 2);
  });

  test('POST /api/register successfully creates student registration (Happy Path)', async () => {
    const payload = {
      registrationType: 'student',
      firstName: 'Luka',
      lastName: 'Krajnc',
      email: 'luka.krajnc@student.um.si',
      studyInstitution: 'Univerza v Mariboru',
      studyProgramme: 'Računalništvo in informacijske tehnologije',
      studentId: 'E1094821',
      selectedOptions: ['ev-keynote'],
      privacyConsent: true,
      website_hp: ''
    };

    const res = await fetch(`${baseUrl}/api/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    assert.equal(res.status, 201, 'Status code must be 201 Created');
    const body = await res.json();
    assert.equal(body.success, true);
    assert.equal(body.data.registrationType, 'student');
    assert.equal(body.data.studyInstitution, 'Univerza v Mariboru');
    assert.equal(body.data.studentId, 'E1094821');
  });

  test('POST /api/register returns 400 Bad Request when required fields are missing', async () => {
    const payload = {
      registrationType: 'external',
      firstName: '',
      lastName: '',
      email: 'invalid-email',
      organization: '',
      privacyConsent: false
    };

    const res = await fetch(`${baseUrl}/api/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    assert.equal(res.status, 400);
    const body = await res.json();
    assert.equal(body.success, false);
    assert.ok(body.details.length >= 4);
  });

  test('POST /api/register returns 400 Bad Request when inactive option is selected', async () => {
    const payload = {
      registrationType: 'external',
      firstName: 'Miran',
      lastName: 'Zorko',
      email: 'miran.zorko@test.si',
      organization: 'Podjetje d.o.o.',
      selectedOptions: ['ws-legacy-migration'], // configured active: false
      privacyConsent: true
    };

    const res = await fetch(`${baseUrl}/api/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    });

    assert.equal(res.status, 400);
    const body = await res.json();
    assert.equal(body.success, false);
    assert.ok(body.details.some(d => d.message.includes('inactive or no longer available')));
  });

  test('GET /api/admin/registrations returns list of registrations', async () => {
    const res = await fetch(`${baseUrl}/api/admin/registrations`);
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.success, true);
    assert.ok(body.total >= 2);
  });

  test('GET /api/admin/export/excel returns .xlsx binary spreadsheet', async () => {
    const res = await fetch(`${baseUrl}/api/admin/export/excel`);
    assert.equal(res.status, 200);
    assert.ok(res.headers.get('content-type').includes('spreadsheetml.sheet'));
    assert.ok(res.headers.get('content-disposition').includes('attachment; filename='));

    const arrayBuffer = await res.arrayBuffer();
    assert.ok(arrayBuffer.byteLength > 100);
  });
});
