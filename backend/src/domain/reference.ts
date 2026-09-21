/**
 * Registration reference generation (specification § 6.1).
 *
 * Format: `REG-<YYYYMMDD>-<10 random Crockford base32 characters>`.
 *
 * The random part is drawn from a cryptographic source because the reference is quoted
 * in the confirmation email and must not be guessable. The alphabet omits I, L, O and U
 * so a reference read aloud or retyped from an email is unambiguous, and contains only
 * characters that are safe in a file name and in a URL (AC-005-03).
 */
import { randomInt } from 'node:crypto';

const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
const RANDOM_LENGTH = 10;

/** Matches exactly the references this module produces; used to reject path traversal. */
export const REFERENCE_PATTERN = /^REG-\d{8}-[0-9ABCDEFGHJKMNPQRSTVWXYZ]{10}$/u;

export function generateReference(now: Date = new Date()): string {
  const datePart = [
    now.getUTCFullYear().toString().padStart(4, '0'),
    (now.getUTCMonth() + 1).toString().padStart(2, '0'),
    now.getUTCDate().toString().padStart(2, '0'),
  ].join('');

  let randomPart = '';
  for (let index = 0; index < RANDOM_LENGTH; index += 1) {
    randomPart += ALPHABET[randomInt(ALPHABET.length)];
  }

  return `REG-${datePart}-${randomPart}`;
}

export function isValidReference(value: string): boolean {
  return REFERENCE_PATTERN.test(value);
}
