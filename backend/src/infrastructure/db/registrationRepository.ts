/**
 * Registration persistence (specification § 3.2, § 6.2).
 *
 * The only module in the application that contains SQL. Every statement is prepared and
 * parameterised, so participant input is never concatenated into a query (AC-G-09).
 */
import type { Database, Statement } from 'better-sqlite3';

import { PersistenceError } from '../../domain/errors.js';
import type {
  OptionGroupId,
  Registration,
  SelectedOption,
} from '../../domain/registration.js';

interface RegistrationRow {
  readonly id: number;
  readonly reference: string;
  readonly variant: string;
  readonly first_name: string;
  readonly last_name: string;
  readonly email: string;
  readonly organization: string | null;
  readonly study_institution: string | null;
  readonly study_programme: string | null;
  readonly student_id: string | null;
  readonly privacy_consent_at: string;
  readonly created_at: string;
  readonly json_backup_file: string;
}

interface OptionRow {
  readonly registration_id: number;
  readonly option_id: string;
  readonly option_group: string;
  readonly display_name: string;
  readonly position: number;
}

export interface StoredRegistration extends Registration {
  readonly jsonBackupFile: string;
}

export class RegistrationRepository {
  private readonly db: Database;
  private readonly insertRegistration: Statement;
  private readonly insertOption: Statement;
  private readonly selectAll: Statement;
  private readonly selectAllOptions: Statement;
  private readonly selectByReference: Statement;
  private readonly countAll: Statement;

  constructor(db: Database) {
    this.db = db;
    this.insertRegistration = db.prepare(`
      INSERT INTO registrations (
        reference, variant, first_name, last_name, email, organization,
        study_institution, study_programme, student_id,
        privacy_consent, privacy_consent_at, created_at, json_backup_file
      ) VALUES (
        @reference, @variant, @firstName, @lastName, @email, @organization,
        @studyInstitution, @studyProgramme, @studentId,
        1, @privacyConsentAt, @createdAt, @jsonBackupFile
      )
    `);
    this.insertOption = db.prepare(`
      INSERT INTO registration_options (registration_id, option_id, option_group, display_name, position)
      VALUES (@registrationId, @optionId, @optionGroup, @displayName, @position)
    `);
    this.selectAll = db.prepare('SELECT * FROM registrations ORDER BY created_at ASC, id ASC');
    this.selectAllOptions = db.prepare(
      'SELECT * FROM registration_options ORDER BY registration_id ASC, position ASC',
    );
    this.selectByReference = db.prepare('SELECT * FROM registrations WHERE reference = ?');
    this.countAll = db.prepare('SELECT COUNT(*) AS total FROM registrations');
  }

  /**
   * Insert a registration and its options, running `sideEffect` inside the same
   * transaction.
   *
   * The JSON backup write is passed in as the side effect so that the database row and
   * the backup file commit or fail together: a throwing side effect rolls back the
   * insert, and a failing commit lets the caller undo the file. That is what makes
   * "either both artefacts exist or neither does" true (AC-005-04).
   *
   * `BEGIN IMMEDIATE` takes the write lock at the start of the transaction, so
   * concurrent submissions serialise instead of failing late with a busy error
   * (AC-005-06).
   */
  insertWithinTransaction(registration: Registration, jsonBackupFile: string, sideEffect: () => void): void {
    const run = this.db.transaction(() => {
      const result = this.insertRegistration.run({
        reference: registration.reference,
        variant: registration.variant,
        firstName: registration.participant.firstName,
        lastName: registration.participant.lastName,
        email: registration.participant.email,
        organization: registration.participant.organization,
        studyInstitution: registration.participant.studyInstitution,
        studyProgramme: registration.participant.studyProgramme,
        studentId: registration.participant.studentId,
        privacyConsentAt: registration.privacyConsentAt,
        createdAt: registration.createdAt,
        jsonBackupFile,
      });

      const registrationId = Number(result.lastInsertRowid);
      registration.selectedOptions.forEach((option, position) => {
        this.insertOption.run({
          registrationId,
          optionId: option.optionId,
          optionGroup: option.group,
          displayName: option.displayName,
          position,
        });
      });

      sideEffect();
    });

    run.immediate();
  }

  existsByReference(reference: string): boolean {
    return this.selectByReference.get(reference) !== undefined;
  }

  count(): number {
    return (this.countAll.get() as { total: number }).total;
  }

  /** Every registration with its options, oldest first — the export data source. */
  findAll(): StoredRegistration[] {
    const rows = this.selectAll.all() as RegistrationRow[];
    const optionRows = this.selectAllOptions.all() as OptionRow[];

    const optionsByRegistration = new Map<number, SelectedOption[]>();
    for (const option of optionRows) {
      const list = optionsByRegistration.get(option.registration_id) ?? [];
      list.push({
        optionId: option.option_id,
        group: option.option_group as OptionGroupId,
        displayName: option.display_name,
      });
      optionsByRegistration.set(option.registration_id, list);
    }

    return rows.map((row) => toStoredRegistration(row, optionsByRegistration.get(row.id) ?? []));
  }

  findByReference(reference: string): StoredRegistration | null {
    const row = this.selectByReference.get(reference) as RegistrationRow | undefined;
    if (row === undefined) {
      return null;
    }
    const options = (
      this.db
        .prepare('SELECT * FROM registration_options WHERE registration_id = ? ORDER BY position ASC')
        .all(row.id) as OptionRow[]
    ).map((option) => ({
      optionId: option.option_id,
      group: option.option_group as OptionGroupId,
      displayName: option.display_name,
    }));
    return toStoredRegistration(row, options);
  }
}

function toStoredRegistration(row: RegistrationRow, options: SelectedOption[]): StoredRegistration {
  if (row.variant !== 'external' && row.variant !== 'student') {
    throw new PersistenceError(`Stored registration has an unknown variant: ${row.variant}`);
  }
  return {
    reference: row.reference,
    variant: row.variant,
    participant: {
      firstName: row.first_name,
      lastName: row.last_name,
      email: row.email,
      organization: row.organization,
      studyInstitution: row.study_institution,
      studyProgramme: row.study_programme,
      studentId: row.student_id,
    },
    selectedOptions: options,
    privacyConsent: true,
    privacyConsentAt: row.privacy_consent_at,
    createdAt: row.created_at,
    jsonBackupFile: row.json_backup_file,
  };
}
