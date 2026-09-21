/**
 * Excel export writer (specification § 8, US-008).
 */
import ExcelJS from 'exceljs';

import { OPTION_GROUPS, type OptionGroupId } from '../../domain/registration.js';
import type { StoredRegistration } from '../db/registrationRepository.js';

const GROUP_COLUMN_HEADERS: Readonly<Record<OptionGroupId, string>> = {
  workshops: 'Workshops',
  events: 'Events',
  meals: 'Meals',
  other: 'Other activities',
};

export const EXPORT_COLUMNS: readonly string[] = [
  'Reference',
  'Created at (UTC)',
  'Participant type',
  'First name',
  'Last name',
  'Email',
  'Organization / institution',
  'Study institution',
  'Study programme',
  'Student ID',
  'Privacy consent',
  'Privacy consent at (UTC)',
  ...OPTION_GROUPS.map((group) => GROUP_COLUMN_HEADERS[group]),
];

/**
 * Neutralise values a spreadsheet application would otherwise evaluate as a formula.
 *
 * A participant can put `=HYPERLINK(...)` in a name field; storing it verbatim is
 * correct, but writing it into a cell unchanged turns the export into code execution on
 * the organizer's machine. Prefixing an apostrophe makes the cell explicit text while
 * keeping the original characters visible (AC-008-08).
 */
export function escapeSpreadsheetValue(value: string): string {
  return /^[=+\-@\t\r]/u.test(value) ? `'${value}` : value;
}

function optionNamesFor(registration: StoredRegistration, group: OptionGroupId): string {
  return registration.selectedOptions
    .filter((option) => option.group === group)
    .map((option) => option.displayName)
    .join('\n');
}

/**
 * Build the registrations workbook.
 *
 * Every cell is written as an explicit string value; no cell is ever given a formula.
 * An empty registration list still produces a valid workbook with the header row
 * (AC-008-06).
 */
export async function buildRegistrationsWorkbook(
  registrations: readonly StoredRegistration[],
): Promise<Buffer> {
  const workbook = new ExcelJS.Workbook();
  workbook.created = new Date();
  const sheet = workbook.addWorksheet('Registrations');

  sheet.addRow([...EXPORT_COLUMNS]);
  sheet.getRow(1).font = { bold: true };
  sheet.views = [{ state: 'frozen', ySplit: 1 }];

  for (const registration of registrations) {
    const participant = registration.participant;
    const values = [
      registration.reference,
      registration.createdAt,
      registration.variant,
      participant.firstName,
      participant.lastName,
      participant.email,
      participant.organization ?? '',
      participant.studyInstitution ?? '',
      participant.studyProgramme ?? '',
      participant.studentId ?? '',
      'Yes',
      registration.privacyConsentAt,
      ...OPTION_GROUPS.map((group) => optionNamesFor(registration, group)),
    ];
    sheet.addRow(values.map(escapeSpreadsheetValue));
  }

  sheet.columns.forEach((column, index) => {
    const header = EXPORT_COLUMNS[index] ?? '';
    column.width = Math.min(Math.max(header.length + 4, 14), 40);
    column.alignment = { vertical: 'top', wrapText: true };
  });

  const written = await workbook.xlsx.writeBuffer();
  return Buffer.from(written);
}
