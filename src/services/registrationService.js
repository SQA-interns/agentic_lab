const crypto = require('node:crypto');
const { validateRegistration } = require('../validators/registrationValidator');
const registrationRepository = require('../db/registrationRepository');
const backupService = require('./backupService');
const emailService = require('./emailService');

class RegistrationService {
  /**
   * Processes a registration submission.
   * @param {object} payload 
   * @param {object} [meta] 
   * @returns {Promise<{ success: boolean, statusCode: number, message?: string, error?: string, details?: any[], data?: object }>}
   */
  async processRegistration(payload, meta = {}) {
    // 1. Validation
    const validation = validateRegistration(payload);
    if (!validation.isValid) {
      return {
        success: false,
        statusCode: 400,
        error: 'Validation failed',
        details: validation.errors
      };
    }

    const clean = validation.sanitizedData;
    const uuid = `reg_${crypto.randomUUID()}`;
    const now = new Date().toISOString();

    const recordToInsert = {
      uuid,
      registrationType: clean.registrationType,
      firstName: clean.firstName,
      lastName: clean.lastName,
      email: clean.email,
      organization: clean.organization,
      studyInstitution: clean.studyInstitution,
      studyProgramme: clean.studyProgramme,
      studentId: clean.studentId,
      selectedOptions: clean.selectedOptions,
      privacyConsent: clean.privacyConsent,
      ipAddress: meta.ipAddress || null,
      userAgent: meta.userAgent || null,
      createdAt: now
    };

    // 2. Relational Database Persistence
    const savedRecord = registrationRepository.insert(recordToInsert);

    // 3. Persistent JSON Backup Storage
    const backupResult = backupService.createBackup(savedRecord, clean.resolvedOptions);

    // 4. Email notifications (participant confirmation + organizer notification)
    await emailService.sendParticipantConfirmation(savedRecord, clean.resolvedOptions);
    await emailService.sendOrganizerNotification(savedRecord, clean.resolvedOptions, backupResult.jsonString);

    // 5. Response formatting
    return {
      success: true,
      statusCode: 201,
      message: 'Registration successfully completed.',
      data: {
        registrationId: savedRecord.uuid,
        registrationType: savedRecord.registrationType,
        firstName: savedRecord.firstName,
        lastName: savedRecord.lastName,
        email: savedRecord.email,
        organization: savedRecord.organization,
        studyInstitution: savedRecord.studyInstitution,
        studyProgramme: savedRecord.studyProgramme,
        studentId: savedRecord.studentId,
        selectedOptions: clean.resolvedOptions,
        submittedAt: savedRecord.createdAt
      }
    };
  }
}

module.exports = new RegistrationService();
