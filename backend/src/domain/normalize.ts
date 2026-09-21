/**
 * Input normalisation (specification § 5.1).
 *
 * Runs before every validation rule so that rules see canonical values:
 * NFC-normalised Unicode, no surrounding whitespace, no control characters.
 */

/**
 * Control characters that must never appear in a single-line field.
 *
 * Rejecting them at the boundary removes header-injection (CR/LF into email headers),
 * log-injection and null-byte vectors before the value reaches any sink (AC-G-09).
 */
// Matching control characters is exactly what this rule is for, so the lint rule that
// warns about them inside a pattern does not apply here.
// eslint-disable-next-line no-control-regex
const CONTROL_CHARACTERS = /[\u0000-\u001F\u007F-\u009F]/u;

/** Unicode whitespace, including the characters `String.prototype.trim` already handles. */
const SURROUNDING_WHITESPACE = /^[\s\uFEFF\u00A0]+|[\s\uFEFF\u00A0]+$/gu;

export interface NormalizeResult {
  /** The canonical value, or null when the field is absent or empty after trimming. */
  readonly value: string | null;
  /** True when the raw input contained a control character and must be rejected. */
  readonly hasControlCharacters: boolean;
}

/**
 * Normalise one single-line text field.
 *
 * Unicode NFC keeps Slovenian characters (č, š, ž) stable and comparable regardless of
 * how the browser composed them (AC-001-10). Leading and trailing whitespace is not
 * significant, and a whitespace-only value is equivalent to an empty one (AC-001-11).
 */
export function normalizeText(input: unknown): NormalizeResult {
  if (typeof input !== 'string') {
    return { value: null, hasControlCharacters: false };
  }

  const composed = input.normalize('NFC');
  if (CONTROL_CHARACTERS.test(composed)) {
    return { value: null, hasControlCharacters: true };
  }

  const trimmed = composed.replace(SURROUNDING_WHITESPACE, '');
  return { value: trimmed.length === 0 ? null : trimmed, hasControlCharacters: false };
}

/** Normalise an email address: same rules as text, plus case-folding of the whole address. */
export function normalizeEmail(input: unknown): NormalizeResult {
  const result = normalizeText(input);
  if (result.value === null) {
    return result;
  }
  return { value: result.value.toLowerCase(), hasControlCharacters: false };
}

/**
 * Normalise the submitted option identifiers: trim, drop blanks, and collapse duplicates
 * while preserving the submitted order (AC-003-09).
 *
 * Values that survive trimming are kept verbatim even when they look malformed, because
 * option validation — not normalisation — is responsible for rejecting identifiers that
 * are unknown or inactive (AC-003-04, AC-003-05). Silently discarding them here would
 * turn a rejectable submission into an accepted one.
 */
export function normalizeOptionIds(input: unknown): string[] {
  if (!Array.isArray(input)) {
    return [];
  }

  const seen = new Set<string>();
  const result: string[] = [];
  for (const entry of input) {
    if (typeof entry !== 'string') {
      continue;
    }
    const value = entry.normalize('NFC').replace(SURROUNDING_WHITESPACE, '');
    if (value.length === 0 || seen.has(value)) {
      continue;
    }
    seen.add(value);
    result.push(value);
  }
  return result;
}
