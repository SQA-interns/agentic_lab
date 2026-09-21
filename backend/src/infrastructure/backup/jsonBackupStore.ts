/**
 * JSON backup store (specification § 3.3, US-005).
 *
 * Every accepted registration is written as one self-contained JSON file on the
 * persistent volume, so the registration list can be rebuilt without the database
 * (AC-005-02, AC-005-08). The organizer notification attaches the very file written here
 * (AC-007-03).
 */
import { closeSync, mkdirSync, openSync, fsyncSync, readFileSync, renameSync, rmSync, writeFileSync } from 'node:fs';
import path from 'node:path';

import { ConfigurationError, PersistenceError } from '../../domain/errors.js';
import { isValidReference } from '../../domain/reference.js';
import type { Registration } from '../../domain/registration.js';

export const BACKUP_SCHEMA_VERSION = 1;

export interface RegistrationBackupDocument {
  readonly schemaVersion: number;
  readonly reference: string;
  readonly variant: string;
  readonly createdAt: string;
  readonly participant: Registration['participant'];
  readonly selectedOptions: Registration['selectedOptions'];
  readonly consent: { readonly privacy: true; readonly grantedAt: string };
  readonly conference: { readonly name: string };
}

export class JsonBackupStore {
  private readonly directory: string;

  constructor(directory: string) {
    this.directory = directory;
    try {
      mkdirSync(directory, { recursive: true });
    } catch (cause) {
      throw new ConfigurationError(
        `The JSON backup directory cannot be created: ${directory} (${cause instanceof Error ? cause.message : String(cause)})`,
      );
    }
  }

  fileNameFor(reference: string): string {
    return `${reference}.json`;
  }

  /**
   * Absolute path of a registration's backup file.
   *
   * The reference is checked against its own pattern first: it is the only part of a
   * file name that comes from generated data, and validating it keeps `..` or separators
   * out of the path no matter what a future caller passes.
   */
  pathFor(reference: string): string {
    if (!isValidReference(reference)) {
      throw new PersistenceError('Refusing to build a backup path from an invalid reference.');
    }
    return path.join(this.directory, this.fileNameFor(reference));
  }

  buildDocument(registration: Registration, conferenceName: string): RegistrationBackupDocument {
    return {
      schemaVersion: BACKUP_SCHEMA_VERSION,
      reference: registration.reference,
      variant: registration.variant,
      createdAt: registration.createdAt,
      participant: registration.participant,
      selectedOptions: registration.selectedOptions,
      consent: { privacy: true, grantedAt: registration.privacyConsentAt },
      conference: { name: conferenceName },
    };
  }

  /**
   * Write a backup file atomically.
   *
   * The content goes to a temporary file which is fsync-ed and then renamed over the
   * final name, and the directory entry is fsync-ed too. A `<reference>.json` that
   * exists is therefore always complete and durable — never a half-written file that a
   * later recovery would silently accept (AC-005-02, AC-005-04).
   */
  write(document: RegistrationBackupDocument): string {
    const target = this.pathFor(document.reference);
    const temporary = `${target}.tmp`;
    const serialized = `${JSON.stringify(document, null, 2)}\n`;

    try {
      writeAndSync(temporary, serialized);
      renameSync(temporary, target);
      syncDirectory(this.directory);
    } catch (cause) {
      rmSync(temporary, { force: true });
      throw new PersistenceError('The registration backup file could not be written.', { cause });
    }

    return target;
  }

  /** Remove a backup file; used to undo a write when the surrounding transaction fails. */
  remove(reference: string): void {
    rmSync(this.pathFor(reference), { force: true });
  }

  /** Read a backup file back from disk, byte for byte, for the organizer attachment. */
  readRaw(reference: string): Buffer {
    try {
      return readFileSync(this.pathFor(reference));
    } catch (cause) {
      throw new PersistenceError('The registration backup file could not be read.', { cause });
    }
  }
}

/**
 * Write a file and flush it to the storage device before returning.
 *
 * The descriptor is opened for writing and fsync-ed through the same handle: a
 * read-only handle cannot be fsync-ed on Windows, and reopening the file would leave a
 * window in which the content is still only in the page cache.
 */
function writeAndSync(filePath: string, contents: string): void {
  const handle = openSync(filePath, 'w');
  try {
    writeFileSync(handle, contents, { encoding: 'utf8' });
    fsyncSync(handle);
  } finally {
    closeSync(handle);
  }
}

function syncDirectory(directory: string): void {
  // Directory fsync is not supported on every platform (notably Windows); the rename
  // itself is still atomic there, so an unsupported fsync must not fail the write.
  let handle: number;
  try {
    handle = openSync(directory, 'r');
  } catch {
    return;
  }
  try {
    fsyncSync(handle);
  } catch {
    /* platform does not support syncing a directory handle */
  } finally {
    closeSync(handle);
  }
}
