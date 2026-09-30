import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

export const mailpitUrl = process.env.E2E_MAILPIT_URL ?? 'http://127.0.0.1:18025';
export const organizerUser = process.env.E2E_ORGANIZER_USERNAME ?? 'organizer';

/** Organizer password for the local synthetic stack (env var or the git-ignored demo file). */
export function organizerPassword(): string {
  const fromEnv = process.env.E2E_ORGANIZER_PASSWORD;
  if (fromEnv) return fromEnv;
  const file = resolve(import.meta.dirname, '../../../.local/organizer-demo-password');
  if (existsSync(file)) return readFileSync(file, 'utf8').trim();
  throw new Error('Set E2E_ORGANIZER_PASSWORD or create 02_output/.local/organizer-demo-password');
}

export function uniqueEmail(prefix: string): string {
  return `${prefix}-${crypto.randomUUID().slice(0, 12)}@example.test`;
}
