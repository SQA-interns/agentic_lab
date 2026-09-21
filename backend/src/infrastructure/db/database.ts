/**
 * SQLite connection management (specification § 3.2, ADR-002).
 */
import { mkdirSync } from 'node:fs';
import path from 'node:path';

import SQLite, { type Database } from 'better-sqlite3';

import { ConfigurationError } from '../../domain/errors.js';
import { runMigrations } from './migrations.js';

export interface OpenDatabaseOptions {
  readonly file: string;
  /** Applied after opening; the defaults are the durability settings of § 3.2. */
  readonly readonly?: boolean;
}

/**
 * Open (creating if necessary) the registration database and bring it to the latest
 * schema version.
 *
 * `journal_mode = WAL` allows the export read to run while a registration is being
 * written; `synchronous = FULL` makes a committed transaction survive an abrupt process
 * or host stop, which is what lets the API promise durability before responding
 * (AC-005-04, AC-005-07). `foreign_keys = ON` keeps option rows tied to their
 * registration.
 */
export function openDatabase(options: OpenDatabaseOptions): Database {
  const directory = path.dirname(options.file);
  try {
    mkdirSync(directory, { recursive: true });
  } catch (cause) {
    throw new ConfigurationError(
      `The database directory cannot be created: ${directory} (${describe(cause)})`,
    );
  }

  let db: Database;
  try {
    db = new SQLite(options.file, { readonly: options.readonly ?? false });
  } catch (cause) {
    throw new ConfigurationError(
      `The database file cannot be opened: ${options.file} (${describe(cause)})`,
    );
  }

  db.pragma('journal_mode = WAL');
  db.pragma('synchronous = FULL');
  db.pragma('foreign_keys = ON');
  db.pragma('busy_timeout = 5000');

  runMigrations(db);
  return db;
}

function describe(cause: unknown): string {
  return cause instanceof Error ? cause.message : String(cause);
}
