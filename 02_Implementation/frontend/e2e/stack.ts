import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import path from 'node:path';

// Helpers that inspect the real docker-compose stack (PostgreSQL, backup volume, Mailpit).
const COMPOSE_DIR = path.resolve(import.meta.dirname, '..', '..');
export const MAILPIT = process.env.MAILPIT_URL ?? 'http://localhost:8025';

function compose(args: string[]): string {
  return execFileSync('docker', ['compose', ...args], { cwd: COMPOSE_DIR, encoding: 'utf8' });
}

/** Runs a SQL query and returns rows as arrays of column strings (unaligned, '|' separated). */
export function sql(query: string): string[][] {
  const out = compose([
    'exec',
    '-T',
    'postgres',
    'psql',
    '-U',
    'conference',
    '-d',
    'conference',
    '-At',
    '-F',
    '|',
    '-c',
    query,
  ]);
  return out
    .split('\n')
    .filter((line) => line.length > 0)
    .map((line) => line.split('|'));
}

export function backupFile(registrationId: string): string {
  if (!/^[0-9a-f-]{36}$/.test(registrationId)) {
    throw new Error('invalid id');
  }
  return compose([
    'exec',
    '-T',
    'backend',
    'cat',
    `/data/backups/registrations/${registrationId}.json`,
  ]);
}

export function sha256(text: string): string {
  return createHash('sha256').update(text, 'utf8').digest('hex');
}

export function uniqueEmail(prefix: string): string {
  return `${prefix}.${Date.now()}.${Math.floor(Math.random() * 1e6)}@example.org`;
}
