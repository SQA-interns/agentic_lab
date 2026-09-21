const { test, describe } = require('node:test');
const assert = require('node:assert/strict');
const { trimString, sanitizeText, isValidEmail } = require('../../src/validators/sanitize');
const { validateRegistration } = require('../../src/validators/registrationValidator');

describe('Unit Tests: Sanitization and Validation', () => {
  test('trimString removes leading and trailing whitespace', () => {
    assert.equal(trimString('   Maribor   '), 'Maribor');
    assert.equal(trimString('   '), '');
    assert.equal(trimString(null), '');
  });

  test('sanitizeText strips dangerous HTML/script tags while preserving Unicode Slovenian characters', () => {
    const input = '<script>alert("xss")</script>Boštjan <b>Žagar</b>';
    const clean = sanitizeText(input);
    assert.equal(clean, 'Boštjan Žagar');
    assert.match(clean, /Boštjan Žagar/);
  });

  test('isValidEmail validates correct and incorrect email addresses', () => {
    assert.equal(isValidEmail('ana.novak@example.com'), true);
    assert.equal(isValidEmail('luka.krajnc@student.um.si'), true);
    assert.equal(isValidEmail('invalid-email'), false);
    assert.equal(isValidEmail('user@'), false);
    assert.equal(isValidEmail('@domain.com'), false);
    assert.equal(isValidEmail('user@domain'), false);
  });

  test('validateRegistration accepts valid external participant payload', () => {
    const payload = {
      registrationType: 'external',
      firstName: '  Mojca  ',
      lastName: '  Novak  ',
      email: '  mojca.novak@podjetje.si  ',
      organization: '  Razvojni center Novo Mesto  ',
      selectedOptions: ['ws-cloud-native', 'meal-vegetarian'],
      privacyConsent: true
    };

    const result = validateRegistration(payload);
    assert.equal(result.isValid, true);
    assert.equal(result.errors.length, 0);
    assert.equal(result.sanitizedData.firstName, 'Mojca');
    assert.equal(result.sanitizedData.lastName, 'Novak');
    assert.equal(result.sanitizedData.organization, 'Razvojni center Novo Mesto');
  });

  test('validateRegistration accepts valid student participant payload', () => {
    const payload = {
      registrationType: 'student',
      firstName: 'Tilen',
      lastName: 'Horvat',
      email: 'tilen.horvat@student.um.si',
      studyInstitution: 'Univerza v Mariboru, FERI',
      studyProgramme: 'Informatika in podatkovne tehnologije',
      studentId: 'E1087654',
      selectedOptions: ['ev-keynote'],
      privacyConsent: true
    };

    const result = validateRegistration(payload);
    assert.equal(result.isValid, true);
    assert.equal(result.errors.length, 0);
    assert.equal(result.sanitizedData.studyInstitution, 'Univerza v Mariboru, FERI');
    assert.equal(result.sanitizedData.studentId, 'E1087654');
  });

  test('validateRegistration rejects payload when mandatory consent is false', () => {
    const payload = {
      registrationType: 'external',
      firstName: 'Janez',
      lastName: 'Novak',
      email: 'janez@example.com',
      organization: 'Podjetje d.o.o.',
      privacyConsent: false
    };

    const result = validateRegistration(payload);
    assert.equal(result.isValid, false);
    assert.ok(result.errors.some(e => e.field === 'privacyConsent'));
  });

  test('validateRegistration rejects external payload missing organization', () => {
    const payload = {
      registrationType: 'external',
      firstName: 'Janez',
      lastName: 'Novak',
      email: 'janez@example.com',
      organization: '',
      privacyConsent: true
    };

    const result = validateRegistration(payload);
    assert.equal(result.isValid, false);
    assert.ok(result.errors.some(e => e.field === 'organization'));
  });

  test('validateRegistration rejects student payload missing student ID or programme', () => {
    const payload = {
      registrationType: 'student',
      firstName: 'Janez',
      lastName: 'Novak',
      email: 'janez@student.si',
      studyInstitution: 'FERI',
      studyProgramme: '',
      studentId: '',
      privacyConsent: true
    };

    const result = validateRegistration(payload);
    assert.equal(result.isValid, false);
    assert.ok(result.errors.some(e => e.field === 'studyProgramme'));
    assert.ok(result.errors.some(e => e.field === 'studentId'));
  });
});
