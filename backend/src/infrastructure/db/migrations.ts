/**
 * Schema migrations (specification § 3.2).
 *
 * Numbered, forward-only steps applied inside a transaction at startup and recorded in
 * `schema_migrations`, so starting an already-initialised container is a no-op and the
 * schema of a mounted volume is upgraded deterministically.
 */
import type { Database } from 'better-sqlite3';

interface Migration {
  readonly version: number;
  readonly name: string;
  readonly sql: string;
}

const MIGRATIONS: readonly Migration[] = [
  {
    version: 1,
    name: 'initial registration schema',
    sql: `
      CREATE TABLE registrations (
        id                 INTEGER PRIMARY KEY AUTOINCREMENT,
        reference          TEXT    NOT NULL UNIQUE,
        variant            TEXT    NOT NULL CHECK (variant IN ('external','student')),
        first_name         TEXT    NOT NULL,
        last_name          TEXT    NOT NULL,
        email              TEXT    NOT NULL,
        organization       TEXT,
        study_institution  TEXT,
        study_programme    TEXT,
        student_id         TEXT,
        privacy_consent    INTEGER NOT NULL CHECK (privacy_consent IN (1)),
        privacy_consent_at TEXT    NOT NULL,
        created_at         TEXT    NOT NULL,
        json_backup_file   TEXT    NOT NULL
      );

      CREATE TABLE registration_options (
        registration_id INTEGER NOT NULL REFERENCES registrations(id) ON DELETE CASCADE,
        option_id       TEXT    NOT NULL,
        option_group    TEXT    NOT NULL,
        display_name    TEXT    NOT NULL,
        position        INTEGER NOT NULL,
        PRIMARY KEY (registration_id, option_id)
      );

      CREATE INDEX idx_registrations_created_at ON registrations(created_at);
    `,
  },
];

export function runMigrations(db: Database): number {
  db.exec(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      version    INTEGER PRIMARY KEY,
      applied_at TEXT NOT NULL
    );
  `);

  const applied = new Set(
    db
      .prepare('SELECT version FROM schema_migrations')
      .all()
      .map((row) => (row as { version: number }).version),
  );

  let count = 0;
  const apply = db.transaction((migration: Migration) => {
    db.exec(migration.sql);
    db.prepare('INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?)').run(
      migration.version,
      new Date().toISOString(),
    );
  });

  for (const migration of MIGRATIONS) {
    if (!applied.has(migration.version)) {
      apply(migration);
      count += 1;
    }
  }
  return count;
}
