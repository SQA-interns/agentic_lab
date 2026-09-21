const fs = require('node:fs');
const path = require('node:path');
const config = require('../../config');

class BackupService {
  constructor(backupDir = config.backupDir) {
    this.backupDir = backupDir;
  }

  /**
   * Ensures the backup directory exists.
   */
  ensureDirectory() {
    if (!fs.existsSync(this.backupDir)) {
      fs.mkdirSync(this.backupDir, { recursive: true });
    }
  }

  /**
   * Formats and writes the standalone JSON backup file.
   * @param {object} registration 
   * @param {Array<{id: string, name: string, category: string}>} resolvedOptions 
   * @returns {{ filePath: string, jsonData: object, jsonString: string }}
   */
  createBackup(registration, resolvedOptions = []) {
    this.ensureDirectory();

    const fileName = `registration_${registration.uuid}.json`;
    const filePath = path.join(this.backupDir, fileName);

    const participantData = {
      firstName: registration.firstName,
      lastName: registration.lastName,
      email: registration.email
    };

    if (registration.registrationType === 'external') {
      participantData.organization = registration.organization;
    } else if (registration.registrationType === 'student') {
      participantData.studyInstitution = registration.studyInstitution;
      participantData.studyProgramme = registration.studyProgramme;
      participantData.studentId = registration.studentId;
    }

    const jsonData = {
      registrationId: registration.uuid,
      registrationType: registration.registrationType,
      participant: participantData,
      selectedOptions: resolvedOptions,
      privacyConsent: registration.privacyConsent,
      submittedAt: registration.createdAt,
      metadata: {
        ipAddress: registration.ipAddress || null,
        userAgent: registration.userAgent || null
      }
    };

    const jsonString = JSON.stringify(jsonData, null, 2);
    fs.writeFileSync(filePath, jsonString, 'utf-8');

    return {
      filePath,
      jsonData,
      jsonString
    };
  }

  /**
   * Reads a backup file by UUID.
   * @param {string} uuid 
   * @returns {object|null}
   */
  readBackup(uuid) {
    const fileName = `registration_${uuid}.json`;
    const filePath = path.join(this.backupDir, fileName);
    if (!fs.existsSync(filePath)) {
      return null;
    }
    const raw = fs.readFileSync(filePath, 'utf-8');
    return JSON.parse(raw);
  }
}

module.exports = new BackupService();
