const { test, describe, beforeEach } = require('node:test');
const assert = require('node:assert/strict');
const emailService = require('../../src/services/emailService');

describe('Integration Tests: Email Notifications Subsystem', () => {
  beforeEach(() => {
    emailService.clearSentEmails();
  });

  test('sendParticipantConfirmation dispatches email with complete summary', async () => {
    const reg = {
      uuid: 'reg_mail_test_101',
      registrationType: 'external',
      firstName: 'Matej',
      lastName: 'Žnidaršič',
      email: 'matej.znidarsic@test.si',
      organization: 'Tehnološki Park',
      createdAt: '2026-09-21T10:00:00.000Z'
    };

    const options = [
      { id: 'ws-cloud-native', name: 'Cloud Native Masterclass', category: 'workshops' },
      { id: 'meal-vegetarian', name: 'Vegetarijanski meni', category: 'meals' }
    ];

    const result = await emailService.sendParticipantConfirmation(reg, options);
    assert.equal(result, true);
    assert.equal(emailService.sentEmails.length, 1);

    const sent = emailService.sentEmails[0];
    assert.equal(sent.to, 'matej.znidarsic@test.si');
    assert.ok(sent.subject.includes('reg_mail_test_101'));
    assert.ok(sent.text.includes('Matej Žnidaršič'));
    assert.ok(sent.text.includes('Cloud Native Masterclass'));
    assert.ok(sent.text.includes('Tehnološki Park'));
  });

  test('sendOrganizerNotification dispatches email with attached JSON file', async () => {
    const reg = {
      uuid: 'reg_mail_test_102',
      registrationType: 'student',
      firstName: 'Nina',
      lastName: 'Kovač',
      email: 'nina.kovac@student.si',
      studyInstitution: 'FERI',
      studyProgramme: 'Računalništvo',
      studentId: 'E109923',
      createdAt: '2026-09-21T10:05:00.000Z'
    };

    const jsonBackupString = JSON.stringify({ registrationId: reg.uuid, participant: reg }, null, 2);

    const result = await emailService.sendOrganizerNotification(reg, [], jsonBackupString);
    assert.equal(result, true);
    assert.equal(emailService.sentEmails.length, 1);

    const sent = emailService.sentEmails[0];
    assert.ok(sent.to);
    assert.ok(sent.subject.includes('Nina Kovač'));
    assert.ok(sent.attachments && sent.attachments.length === 1);
    assert.equal(sent.attachments[0].filename, 'registration_reg_mail_test_102.json');
    assert.equal(sent.attachments[0].contentType, 'application/json');
    assert.ok(sent.attachments[0].content.includes('reg_mail_test_102'));
  });
});
