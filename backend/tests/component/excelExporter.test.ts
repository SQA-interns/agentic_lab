/**
 * Component tests — Excel workbook writer (specification § 8, US-008).
 *
 * Level justification: the deliverable is a binary file an organizer opens in a
 * spreadsheet application. The tests write a real workbook and read it back with the
 * same library, so they check the artefact rather than the calls that produced it.
 */
import ExcelJS from 'exceljs';
import { describe, expect, it } from 'vitest';

import {
  buildRegistrationsWorkbook,
  escapeSpreadsheetValue,
  EXPORT_COLUMNS,
} from '../../src/infrastructure/excel/excelExporter.js';
import type { StoredRegistration } from '../../src/infrastructure/db/registrationRepository.js';

function storedRegistration(overrides: Partial<StoredRegistration> = {}): StoredRegistration {
  return {
    reference: 'REG-20260921-ABCDEFGHJK',
    variant: 'external',
    participant: {
      firstName: 'Ana',
      lastName: 'Novak',
      email: 'ana@example.org',
      organization: 'Univerza v Mariboru',
      studyInstitution: null,
      studyProgramme: null,
      studentId: null,
    },
    selectedOptions: [
      { optionId: 'workshop-ai', group: 'workshops', displayName: 'Workshop: AI' },
      { optionId: 'meal-lunch', group: 'meals', displayName: 'Lunch' },
    ],
    privacyConsent: true,
    privacyConsentAt: '2026-09-21T10:00:00.000Z',
    createdAt: '2026-09-21T10:00:00.000Z',
    jsonBackupFile: 'REG-20260921-ABCDEFGHJK.json',
    ...overrides,
  };
}

async function readBack(buffer: Buffer): Promise<ExcelJS.Worksheet> {
  const workbook = new ExcelJS.Workbook();
  await workbook.xlsx.load(buffer as unknown as ArrayBuffer);
  const sheet = workbook.getWorksheet('Registrations');
  if (sheet === undefined) {
    throw new Error('worksheet "Registrations" is missing');
  }
  return sheet;
}

/** Render a cell the way a spreadsheet application would show it. */
function cellText(value: unknown): string {
  if (value === undefined || value === null) {
    return '';
  }
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  // A rich-text or formula cell would land here; the export writes neither, so seeing
  // one is itself a failure worth surfacing in the assertion.
  return JSON.stringify(value);
}

function rowValues(sheet: ExcelJS.Worksheet, rowNumber: number): string[] {
  const values = sheet.getRow(rowNumber).values as unknown[];
  // ExcelJS pads index 0; drop it so the array lines up with EXPORT_COLUMNS.
  return values.slice(1).map(cellText);
}

describe('escapeSpreadsheetValue', () => {
  it.each([['=1+1'], ['+1'], ['-1'], ['@SUM(A1)'], ['\tx']])(
    'prefixes %s so a spreadsheet does not evaluate it (AC-008-08)',
    (value) => {
      expect(escapeSpreadsheetValue(value)).toBe(`'${value}`);
    },
  );

  it.each([['Ana'], ['ana@example.org'], ['Čenčič'], ['']])('leaves %s unchanged', (value) => {
    expect(escapeSpreadsheetValue(value)).toBe(value);
  });
});

describe('buildRegistrationsWorkbook', () => {
  it('produces a readable workbook with a header row and one row per registration (AC-008-02)', async () => {
    const sheet = await readBack(
      await buildRegistrationsWorkbook([
        storedRegistration(),
        storedRegistration({ reference: 'REG-20260921-BBBBBBBBBB' }),
      ]),
    );
    expect(rowValues(sheet, 1)).toEqual([...EXPORT_COLUMNS]);
    expect(sheet.rowCount).toBe(3);
  });

  it('covers the fixed fields of both variants, leaving inapplicable ones empty (AC-008-03)', async () => {
    const sheet = await readBack(
      await buildRegistrationsWorkbook([
        storedRegistration(),
        storedRegistration({
          reference: 'REG-20260921-CCCCCCCCCC',
          variant: 'student',
          participant: {
            firstName: 'Žan',
            lastName: 'Šuštaršič',
            email: 'zan@example.org',
            organization: null,
            studyInstitution: 'Univerza v Mariboru',
            studyProgramme: 'Računalništvo',
            studentId: 'F1234567',
          },
        }),
      ]),
    );

    const header = rowValues(sheet, 1);
    const external = rowValues(sheet, 2);
    const student = rowValues(sheet, 3);
    const at = (label: string, row: string[]): string => row[header.indexOf(label)] ?? '';

    expect(at('Participant type', external)).toBe('external');
    expect(at('Organization / institution', external)).toBe('Univerza v Mariboru');
    expect(at('Study institution', external)).toBe('');
    expect(at('Student ID', external)).toBe('');

    expect(at('Participant type', student)).toBe('student');
    expect(at('Organization / institution', student)).toBe('');
    expect(at('Study programme', student)).toBe('Računalništvo');
    expect(at('Student ID', student)).toBe('F1234567');

    expect(at('Privacy consent', external)).toBe('Yes');
    expect(at('Privacy consent at (UTC)', external)).toBe('2026-09-21T10:00:00.000Z');
  });

  it('lists selected options in the column of their group', async () => {
    const sheet = await readBack(await buildRegistrationsWorkbook([storedRegistration()]));
    const header = rowValues(sheet, 1);
    const row = rowValues(sheet, 2);
    expect(row[header.indexOf('Workshops')]).toBe('Workshop: AI');
    expect(row[header.indexOf('Meals')]).toBe('Lunch');
    expect(row[header.indexOf('Events')]).toBe('');
  });

  it('preserves Slovenian characters (AC-008-05)', async () => {
    const sheet = await readBack(
      await buildRegistrationsWorkbook([
        storedRegistration({
          participant: {
            firstName: 'Špela',
            lastName: 'Čenčič',
            email: 'spela@example.org',
            organization: 'Žito d.d.',
            studyInstitution: null,
            studyProgramme: null,
            studentId: null,
          },
        }),
      ]),
    );
    expect(rowValues(sheet, 2)).toContain('Špela');
    expect(rowValues(sheet, 2)).toContain('Čenčič');
    expect(rowValues(sheet, 2)).toContain('Žito d.d.');
  });

  it('writes a header-only workbook when there are no registrations (AC-008-06)', async () => {
    const sheet = await readBack(await buildRegistrationsWorkbook([]));
    expect(rowValues(sheet, 1)).toEqual([...EXPORT_COLUMNS]);
    expect(sheet.rowCount).toBe(1);
  });

  it('writes formula-leading participant values as text, not as formulas (AC-008-08)', async () => {
    const sheet = await readBack(
      await buildRegistrationsWorkbook([
        storedRegistration({
          participant: {
            firstName: '=HYPERLINK("http://evil.example","click")',
            lastName: 'Novak',
            email: 'a@b.org',
            organization: 'X',
            studyInstitution: null,
            studyProgramme: null,
            studentId: null,
          },
        }),
      ]),
    );
    const cell = sheet.getRow(2).getCell(4);
    expect(cell.type).toBe(ExcelJS.ValueType.String);
    expect(cellText(cell.value).startsWith("'=")).toBe(true);
  });
});
