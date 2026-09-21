/**
 * Unit tests — registration reference generation (specification § 6.1, AC-005-03).
 *
 * Level justification: the reference must be unique, unpredictable and safe to use in a
 * file name. Those properties are statistical and structural, so they are checked here
 * over many samples rather than through a handful of HTTP requests.
 */
import { describe, expect, it } from 'vitest';

import { generateReference, isValidReference } from '../../src/domain/reference.js';

describe('generateReference', () => {
  it('uses the REG-<date>-<random> format', () => {
    const reference = generateReference(new Date('2026-09-21T10:00:00.000Z'));
    expect(reference).toMatch(/^REG-20260921-[0-9ABCDEFGHJKMNPQRSTVWXYZ]{10}$/u);
  });

  it('uses the UTC date, not the local one', () => {
    // 23:30 UTC on the 21st is already the 22nd in some time zones; the reference must
    // follow UTC so it is the same wherever the server runs.
    expect(generateReference(new Date('2026-09-21T23:30:00.000Z')).startsWith('REG-20260921-')).toBe(true);
  });

  it('omits the ambiguous letters I, L, O and U', () => {
    const sample = Array.from({ length: 200 }, () => generateReference()).join('');
    expect(sample).not.toMatch(/[ILOU]/u);
  });

  it('produces distinct values', () => {
    const references = new Set(Array.from({ length: 2000 }, () => generateReference()));
    expect(references.size).toBe(2000);
  });

  it('contains only characters that are safe in a file name and a URL', () => {
    for (let index = 0; index < 100; index += 1) {
      expect(generateReference()).toMatch(/^[A-Z0-9-]+$/u);
    }
  });
});

describe('isValidReference', () => {
  it('accepts a generated reference', () => {
    expect(isValidReference(generateReference())).toBe(true);
  });

  it.each([
    ['../../etc/passwd'],
    ['REG-20260921-ABCDEFGHI'],
    ['REG-2026921-ABCDEFGHJK'],
    ['reg-20260921-ABCDEFGHJK'],
    ['REG-20260921-ABCDEFGHJK/..'],
    [''],
  ])('rejects %s, which keeps path traversal out of backup file names', (value) => {
    expect(isValidReference(value)).toBe(false);
  });
});
