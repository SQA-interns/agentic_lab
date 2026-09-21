const { getDatabase } = require('./database');

class RegistrationRepository {
  /**
   * Persists a registration record into SQLite.
   * @param {object} reg 
   * @returns {object} created record
   */
  insert(reg) {
    const db = getDatabase();
    const stmt = db.prepare(`
      INSERT INTO registrations (
        uuid,
        registration_type,
        first_name,
        last_name,
        email,
        organization,
        study_institution,
        study_programme,
        student_id,
        selected_options,
        privacy_consent,
        ip_address,
        user_agent,
        created_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    `);

    const createdAt = reg.createdAt || new Date().toISOString();
    const selectedOptionsJson = JSON.stringify(reg.selectedOptions || []);

    stmt.run(
      reg.uuid,
      reg.registrationType,
      reg.firstName,
      reg.lastName,
      reg.email,
      reg.organization || null,
      reg.studyInstitution || null,
      reg.studyProgramme || null,
      reg.studentId || null,
      selectedOptionsJson,
      reg.privacyConsent ? 1 : 0,
      reg.ipAddress || null,
      reg.userAgent || null,
      createdAt
    );

    return this.findByUuid(reg.uuid);
  }

  /**
   * Finds registration by UUID.
   * @param {string} uuid 
   * @returns {object|null}
   */
  findByUuid(uuid) {
    const db = getDatabase();
    const stmt = db.prepare(`SELECT * FROM registrations WHERE uuid = ?`);
    const row = stmt.get(uuid);
    if (!row) return null;
    return this._mapRow(row);
  }

  /**
   * Retrieves all registrations ordered by creation time descending.
   * @returns {object[]}
   */
  findAll() {
    const db = getDatabase();
    const stmt = db.prepare(`SELECT * FROM registrations ORDER BY id ASC`);
    const rows = stmt.all();
    return rows.map(r => this._mapRow(r));
  }

  /**
   * Returns total count of registrations.
   * @returns {number}
   */
  count() {
    const db = getDatabase();
    const stmt = db.prepare(`SELECT COUNT(*) as cnt FROM registrations`);
    const row = stmt.get();
    return row ? Number(row.cnt) : 0;
  }

  /**
   * Maps SQLite row to domain object.
   * @private
   */
  _mapRow(row) {
    let options = [];
    try {
      options = JSON.parse(row.selected_options);
    } catch (e) {
      options = [];
    }

    return {
      id: row.id,
      uuid: row.uuid,
      registrationType: row.registration_type,
      firstName: row.first_name,
      lastName: row.last_name,
      email: row.email,
      organization: row.organization,
      studyInstitution: row.study_institution,
      studyProgramme: row.study_programme,
      studentId: row.student_id,
      selectedOptions: options,
      privacyConsent: Boolean(row.privacy_consent),
      ipAddress: row.ip_address,
      userAgent: row.user_agent,
      createdAt: row.created_at
    };
  }
}

module.exports = new RegistrationRepository();
