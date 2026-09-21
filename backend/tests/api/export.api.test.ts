/**
 * REST API tests — GET /api/export/registrations.xlsx (specification § 4.4, US-008).
 *
 * Level justification: the export is an authenticated HTTP endpoint that returns a
 * binary body with specific headers. Only an API-level test can check the authentication
 * boundary, the content type and the download headers together with the payload.
 */
import ExcelJS from 'exceljs';
import request from 'supertest';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { EXPORT_AUTH, registerExternal, registerStudent } from '../helpers/requests.js';
import { createTestApplication, type TestApplication } from '../helpers/testApplication.js';

const EXPORT_URL = '/api/export/registrations.xlsx';
const XLSX_TYPE = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';

let application: TestApplication;

beforeEach(() => {
  application = createTestApplication();
});

afterEach(() => {
  application.dispose();
});

async function sheetFrom(body: Buffer): Promise<ExcelJS.Worksheet> {
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(body as unknown as ArrayBuffer);
  const sheet = workbook.getWorksheet('Registrations');
  if (sheet === undefined) {
    throw new Error('worksheet missing');
  }
  return sheet;
}

describe('GET /api/export/registrations.xlsx — authentication (AC-008-07)', () => {
  it('rejects an unauthenticated request and returns no data', async () => {
    await registerExternal(application.app);
    const response = await request(application.app).get(EXPORT_URL);

    expect(response.status).toBe(401);
    expect(response.headers['www-authenticate']).toContain('Basic');
    expect(JSON.stringify(response.body)).not.toContain('ana.novak@example.org');
  });

  it.each([
    ['wrong password', 'organizer:wrong-password'],
    ['wrong username', 'intruder:organizer-password'],
    ['empty credentials', ':'],
  ])('rejects %s', async (_label, credentials) => {
    const response = await request(application.app)
      .get(EXPORT_URL)
      .set('Authorization', `Basic ${Buffer.from(credentials).toString('base64')}`);
    expect(response.status).toBe(401);
  });

  it('rejects a non-Basic Authorization header', async () => {
    const response = await request(application.app).get(EXPORT_URL).set('Authorization', 'Bearer token');
    expect(response.status).toBe(401);
  });

  it('accepts the configured credentials', async () => {
    const response = await request(application.app).get(EXPORT_URL).set('Authorization', EXPORT_AUTH);
    expect(response.status).toBe(200);
  });
});

describe('GET /api/export/registrations.xlsx — response (AC-008-01, AC-008-09)', () => {
  it('returns an Excel workbook with download headers', async () => {
    const response = await request(application.app)
      .get(EXPORT_URL)
      .set('Authorization', EXPORT_AUTH)
      .buffer()
      .parse((res, callback) => {
        const chunks: Buffer[] = [];
        res.on('data', (chunk: Buffer) => chunks.push(chunk));
        res.on('end', () => callback(null, Buffer.concat(chunks)));
      });

    expect(response.status).toBe(200);
    expect(response.headers['content-type']).toContain(XLSX_TYPE);
    expect(response.headers['content-disposition']).toMatch(
      /attachment; filename="registrations-\d{8}-\d{6}\.xlsx"/u,
    );
    // The export contains personal data, so no intermediary may cache it.
    expect(response.headers['cache-control']).toBe('no-store');
    // A real OOXML file is a ZIP container.
    expect((response.body as Buffer).subarray(0, 2).toString()).toBe('PK');
  });

  it('returns a header-only workbook when nothing is registered (AC-008-06)', async () => {
    const response = await request(application.app)
      .get(EXPORT_URL)
      .set('Authorization', EXPORT_AUTH)
      .buffer()
      .parse((res, callback) => {
        const chunks: Buffer[] = [];
        res.on('data', (chunk: Buffer) => chunks.push(chunk));
        res.on('end', () => callback(null, Buffer.concat(chunks)));
      });

    const sheet = await sheetFrom(response.body as Buffer);
    expect(sheet.rowCount).toBe(1);
  });

  it('includes registrations accepted before the request (AC-008-04)', async () => {
    await registerExternal(application.app, { email: 'first@example.org' });
    await registerStudent(application.app, { email: 'second@example.org' });

    const response = await request(application.app)
      .get(EXPORT_URL)
      .set('Authorization', EXPORT_AUTH)
      .buffer()
      .parse((res, callback) => {
        const chunks: Buffer[] = [];
        res.on('data', (chunk: Buffer) => chunks.push(chunk));
        res.on('end', () => callback(null, Buffer.concat(chunks)));
      });

    const sheet = await sheetFrom(response.body as Buffer);
    expect(sheet.rowCount).toBe(3);
    const text = JSON.stringify(sheet.getSheetValues());
    expect(text).toContain('first@example.org');
    expect(text).toContain('second@example.org');
    expect(text).toContain('Šuštaršič');
  });

  it('exports a registration that was accepted only moments earlier', async () => {
    const before = await request(application.app)
      .get(EXPORT_URL)
      .set('Authorization', EXPORT_AUTH)
      .buffer()
      .parse((res, callback) => {
        const chunks: Buffer[] = [];
        res.on('data', (chunk: Buffer) => chunks.push(chunk));
        res.on('end', () => callback(null, Buffer.concat(chunks)));
      });
    expect((await sheetFrom(before.body as Buffer)).rowCount).toBe(1);

    await registerExternal(application.app);

    const after = await request(application.app)
      .get(EXPORT_URL)
      .set('Authorization', EXPORT_AUTH)
      .buffer()
      .parse((res, callback) => {
        const chunks: Buffer[] = [];
        res.on('data', (chunk: Buffer) => chunks.push(chunk));
        res.on('end', () => callback(null, Buffer.concat(chunks)));
      });
    expect((await sheetFrom(after.body as Buffer)).rowCount).toBe(2);
  });
});
