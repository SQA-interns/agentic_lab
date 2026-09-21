const { test, describe, before, after } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { initDatabase, closeDatabase } = require('../../src/db/database');
const registrationRepository = require('../../src/db/registrationRepository');
const backupService = require('../../src/services/backupService');

describe('Integration Tests: Dual Storage (SQLite + JSON Backup)', () => {
  const testDbPath = ':memory:';
  const testBackupDir = path.join(__dirname, '..', 'tmp_backups');

  before(() => {
    initDatabase(testDbPath);
    backupService.backupDir = testBackupDir;
  });

  after(() => {
    closeDatabase();
    if (fs.existsSync(testBackupDir)) {
      fs.rmSync(testBackupDir, { recursive: true, force: true });
    }
  });

  test('Persists external registration in SQLite and verifies row fields', () => {
    const reg = {
      uuid: 'reg_test_uuid_001',
      registrationType: 'external',
      firstName: 'Marjan',
      lastName: 'Kralj',
      email: 'marjan.kralj@podjetje.si',
      organization: 'Razvojna Agencija',
      selectedOptions: ['ws-cloud-native', 'meal-vegetarian'],
      privacyConsent: true,
      ipAddress: '127.0.0.1',
      userAgent: 'IntegrationTest/1.0',
      createdAt: new Date().toISOString()
    };

    const inserted = registrationRepository.insert(reg);
    assert.ok(inserted.id, 'Record should have database numeric ID');
    assert.equal(inserted.uuid, 'reg_test_uuid_001');
    assert.equal(inserted.firstName, 'Marjan');
    assert.equal(inserted.organization, 'Razvojna Agencija');
    assert.deepEqual(inserted.selectedOptions, ['ws-cloud-native', 'meal-vegetarian']);

    const retrieved = registrationRepository.findByUuid('reg_test_uuid_001');
    assert.equal(retrieved.uuid, inserted.uuid);
    assert.equal(retrieved.email, 'marjan.kralj@podjetje.si');
  });

  test('Creates persistent JSON backup file with complete schema fidelity', () => {
    const reg = {
      uuid: 'reg_test_uuid_002',
      registrationType: 'student',
      firstName: 'Maja',
      lastName: 'Čeh',
      email: 'maja.ceh@student.um.si',
      studyInstitution: 'Univerza v Mariboru',
      studyProgramme: 'Medijske komunikacije',
      studentId: 'E103984',
      selectedOptions: ['ev-keynote'],
      privacyConsent: true,
      ipAddress: '127.0.0.1',
      userAgent: 'IntegrationTest/1.0',
      createdAt: new Date().toISOString()
    };

    const resolvedOptions = [{
      id: 'ev-keynote',
      name: 'Otvoritveno predavanje',
      category: 'events'
    }];

    const backupResult = backupService.createBackup(reg, resolvedOptions);
    assert.ok(fs.existsSync(backupResult.filePath), 'Backup file must exist on disk');

    const fileContent = JSON.parse(fs.readFileSync(backupResult.filePath, 'utf-8'));
    assert.equal(fileContent.registrationId, 'reg_test_uuid_002');
    assert.equal(fileContent.registrationType, 'student');
    assert.equal(fileContent.participant.firstName, 'Maja');
    assert.equal(fileContent.participant.lastName, 'Čeh');
    assert.equal(fileContent.participant.studyInstitution, 'Univerza v Mariboru');
    assert.equal(fileContent.participant.studentId, 'E103984');
    assert.equal(fileContent.selectedOptions[0].name, 'Otvoritveno predavanje');
  });
});
