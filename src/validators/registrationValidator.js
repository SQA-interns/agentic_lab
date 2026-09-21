const { trimString, sanitizeText, isValidEmail } = require('./sanitize');
const optionsService = require('../services/optionsService');

/**
 * Validates external or student registration payload.
 * @param {object} payload 
 * @returns {{ isValid: boolean, errors: Array<{field: string, message: string}>, sanitizedData: object }}
 */
function validateRegistration(payload) {
  const errors = [];
  const raw = payload || {};

  const registrationType = trimString(raw.registrationType);
  if (!registrationType || (registrationType !== 'external' && registrationType !== 'student')) {
    errors.push({
      field: 'registrationType',
      message: 'Registration type must be either "external" or "student".'
    });
  }

  // Anti-bot honeypot check
  const honeypot = raw.honeypot || raw.website_hp;
  if (honeypot && typeof honeypot === 'string' && honeypot.trim().length > 0) {
    errors.push({
      field: 'honeypot',
      message: 'Automated submission detected.'
    });
  }

  // Mandatory consent check
  const privacyConsent = raw.privacyConsent === true || raw.privacyConsent === 'true' || raw.privacyConsent === 1;
  if (!privacyConsent) {
    errors.push({
      field: 'privacyConsent',
      message: 'Consent to personal data processing is required.'
    });
  }

  // Common fixed fields
  const firstName = sanitizeText(raw.firstName);
  if (!firstName) {
    errors.push({
      field: 'firstName',
      message: 'First name is required.'
    });
  }

  const lastName = sanitizeText(raw.lastName);
  if (!lastName) {
    errors.push({
      field: 'lastName',
      message: 'Last name is required.'
    });
  }

  const email = trimString(raw.email);
  if (!email) {
    errors.push({
      field: 'email',
      message: 'Email address is required.'
    });
  } else if (!isValidEmail(email)) {
    errors.push({
      field: 'email',
      message: 'A valid email address is required.'
    });
  }

  // Type-specific fixed fields
  let organization = null;
  let studyInstitution = null;
  let studyProgramme = null;
  let studentId = null;

  if (registrationType === 'external') {
    organization = sanitizeText(raw.organization);
    if (!organization) {
      errors.push({
        field: 'organization',
        message: 'Organization or institution name is required.'
      });
    }
  } else if (registrationType === 'student') {
    studyInstitution = sanitizeText(raw.studyInstitution);
    if (!studyInstitution) {
      errors.push({
        field: 'studyInstitution',
        message: 'Study institution is required.'
      });
    }

    studyProgramme = sanitizeText(raw.studyProgramme);
    if (!studyProgramme) {
      errors.push({
        field: 'studyProgramme',
        message: 'Study programme is required.'
      });
    }

    studentId = sanitizeText(raw.studentId);
    if (!studentId) {
      errors.push({
        field: 'studentId',
        message: 'Student ID is required.'
      });
    }
  }

  // Configurable options validation
  const selectedOptions = Array.isArray(raw.selectedOptions) ? raw.selectedOptions : [];
  const optionsValidation = optionsService.validateSelectedOptions(selectedOptions);
  if (!optionsValidation.valid) {
    for (const optErr of optionsValidation.errors) {
      errors.push({
        field: 'selectedOptions',
        message: optErr
      });
    }
  }

  const sanitizedData = {
    registrationType,
    firstName,
    lastName,
    email,
    organization,
    studyInstitution,
    studyProgramme,
    studentId,
    selectedOptions,
    resolvedOptions: optionsValidation.resolvedOptions,
    privacyConsent: true
  };

  return {
    isValid: errors.length === 0,
    errors,
    sanitizedData
  };
}

module.exports = {
  validateRegistration
};
