const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');
const ExcelJS = require('exceljs');
const { initDatabase, closeDatabase } = require('../../src/db/database');
const registrationRepository = require('../../src/db/registrationRepository');
const exportService = require('../../src/services/exportService');

describe('Integration Tests: Excel (.xlsx) Export', () => {
  before(() => {
    initDatabase(':memory:');
  });

  after(() => {
    closeDatabase();
  });

  test('generateRegistrationsExcelBuffer generates valid empty workbook when no registrations exist', async () => {
    const buffer = await exportService.generateRegistrationsExcelBuffer();
    assert.ok(buffer && buffer.length > 0, 'Buffer should not be empty');

    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(buffer);
    const sheet = workbook.getWorksheet('Prijave');
    assert.ok(sheet, 'Worksheet "Prijave" must exist');

    // Header row should exist
    assert.equal(sheet.getRow(1).getCell(1).value, 'ID prijave');
    assert.equal(sheet.rowCount, 1, 'Empty DB should have exactly 1 row (header only)');
  });

  test('generateRegistrationsExcelBuffer exports data rows with Slovenian Unicode characters and options', async () => {
    registrationRepository.insert({
      uuid: 'reg_export_001',
      registrationType: 'external',
      firstName: 'Boštjan',
      lastName: 'Železnik',
      email: 'bostjan.zeleznik@maribor.si',
      organization: 'Mestna občina Maribor',
      selectedOptions: ['ws-cloud-native', 'meal-standard'],
      privacyConsent: true,
      createdAt: '2026-09-21T11:00:00.000Z'
    });

    registrationRepository.insert({
      uuid: 'reg_export_002',
      registrationType: 'student',
      firstName: 'Špela',
      lastName: 'Čop',
      email: 'spela.cop@student.um.si',
      studyInstitution: 'Univerza v Mariboru',
      studyProgramme: 'Telekomunikacije',
      studentId: 'E104567',
      selectedOptions: ['ev-keynote'],
      privacyConsent: true,
      createdAt: '2026-09-21T11:05:00.000Z'
    });

    const buffer = await exportService.generateRegistrationsExcelBuffer();
    const workbook = new ExcelJS.Workbook();
    await workbook.xlsx.load(buffer);
    const sheet = workbook.getWorksheet('Prijave');

    assert.equal(sheet.rowCount, 3, 'Should have header + 2 registration rows');

    // Row 2 verification (External)
    const row2 = sheet.getRow(2);
    assert.equal(row2.getCell(1).value, 'reg_export_001');
    assert.equal(row2.getCell(3).value, 'Zunanji udeleženec');
    assert.equal(row2.getCell(4).value, 'Boštjan');
    assert.equal(row2.getCell(5).value, 'Železnik');
    assert.equal(row2.getCell(7).value, 'Mestna občina Maribor');
    assert.ok(row2.getCell(10).value.includes('Cloud Native Architecture Masterclass'));

    // Row 3 verification (Student)
    const row3 = sheet.getRow(3);
    assert.equal(row3.getCell(1).value, 'reg_export_002');
    assert.equal(row3.getCell(3).value, 'Študent');
    assert.equal(row3.getCell(4).value, 'Špela');
    assert.equal(row3.getCell(5).value, 'Čop');
    assert.equal(row3.getCell(8).value, 'Telekomunikacije');
    assert.equal(row3.getCell(9).value, 'E104567');
  });
});
