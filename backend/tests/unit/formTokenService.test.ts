/**
 * Unit tests — anti-automation form tokens (specification § 11, AC-G-10).
 *
 * Level justification: the control is time- and secret-dependent. A unit test with an
 * injected clock is the only practical way to cover expiry, the minimum fill time and
 * replay without real waiting, and it lets each rejection reason be asserted
 * individually — deliberately impossible through the API, where all reasons look alike.
 */
import { describe, expect, it } from 'vitest';

import { FormTokenService } from '../../src/application/formTokenService.js';
import { AntiAutomationError } from '../../src/domain/errors.js';

function serviceAt(
  nowMs: { value: number },
  overrides: Partial<{ ttlSeconds: number; minFillSeconds: number }> = {},
): FormTokenService {
  return new FormTokenService({
    secret: 'unit-test-secret-value-long-enough-for-hmac',
    ttlSeconds: overrides.ttlSeconds ?? 1800,
    minFillSeconds: overrides.minFillSeconds ?? 3,
    now: () => nowMs.value,
  });
}

function reasonOf(fn: () => void): string {
  try {
    fn();
  } catch (error) {
    if (error instanceof AntiAutomationError) {
      return error.reason;
    }
    throw error;
  }
  throw new Error('expected an AntiAutomationError');
}

describe('FormTokenService', () => {
  it('accepts a token issued for the same variant after the minimum fill time', () => {
    const now = { value: 1_000_000_000_000 };
    const service = serviceAt(now);
    const token = service.issue('external');
    now.value += 5_000;
    expect(() => service.consume(token, 'external')).not.toThrow();
  });

  it('issues a different token every time', () => {
    const now = { value: 1_000_000_000_000 };
    const service = serviceAt(now);
    expect(service.issue('external')).not.toBe(service.issue('external'));
  });

  it('rejects a token whose signature was tampered with', () => {
    const now = { value: 1_000_000_000_000 };
    const service = serviceAt(now);
    const token = service.issue('external');
    now.value += 5_000;
    expect(reasonOf(() => service.consume(`${token}x`, 'external'))).toBe('token_signature_invalid');
  });

  it('rejects a token whose payload was tampered with', () => {
    const now = { value: 1_000_000_000_000 };
    const service = serviceAt(now);
    const token = service.issue('external');
    const [payload, signature] = token.split('.');
    const forged = Buffer.from(
      JSON.stringify({ nonce: 'forged', variant: 'external', issuedAt: 0 }),
    ).toString('base64url');
    now.value += 5_000;
    expect(reasonOf(() => service.consume(`${forged}.${signature ?? ''}`, 'external'))).toBe(
      'token_signature_invalid',
    );
    expect(payload).not.toBe(forged);
  });

  it('rejects a token signed with a different secret', () => {
    const now = { value: 1_000_000_000_000 };
    const other = new FormTokenService({
      secret: 'a-completely-different-secret-value-here',
      ttlSeconds: 1800,
      minFillSeconds: 0,
      now: () => now.value,
    });
    const token = other.issue('external');
    expect(reasonOf(() => serviceAt(now).consume(token, 'external'))).toBe('token_signature_invalid');
  });

  it('rejects a malformed token', () => {
    const now = { value: 1_000_000_000_000 };
    expect(reasonOf(() => serviceAt(now).consume('no-separator', 'external'))).toBe('token_malformed');
  });

  it('rejects a token issued for the other variant', () => {
    const now = { value: 1_000_000_000_000 };
    const service = serviceAt(now);
    const token = service.issue('student');
    now.value += 5_000;
    expect(reasonOf(() => service.consume(token, 'external'))).toBe('token_variant_mismatch');
  });

  it('rejects a token older than its lifetime', () => {
    const now = { value: 1_000_000_000_000 };
    const service = serviceAt(now, { ttlSeconds: 60 });
    const token = service.issue('external');
    now.value += 61_000;
    expect(reasonOf(() => service.consume(token, 'external'))).toBe('token_expired');
  });

  it('rejects a submission faster than a person could fill the form', () => {
    const now = { value: 1_000_000_000_000 };
    const service = serviceAt(now, { minFillSeconds: 3 });
    const token = service.issue('external');
    now.value += 1_000;
    expect(reasonOf(() => service.consume(token, 'external'))).toBe('submitted_too_fast');
  });

  it('rejects a replayed token: one token buys exactly one registration', () => {
    const now = { value: 1_000_000_000_000 };
    const service = serviceAt(now);
    const token = service.issue('external');
    now.value += 5_000;
    service.consume(token, 'external');
    expect(reasonOf(() => service.consume(token, 'external'))).toBe('token_replayed');
  });

  it('returns the same opaque message whatever the reason, so bots learn nothing', () => {
    const now = { value: 1_000_000_000_000 };
    const service = serviceAt(now);
    const messages = new Set<string>();
    for (const attempt of ['bad', `${service.issue('student')}`]) {
      try {
        service.consume(attempt, 'external');
      } catch (error) {
        messages.add((error as Error).message);
      }
    }
    expect(messages.size).toBe(1);
  });

  it('accepts an absent or empty honeypot value', () => {
    const now = { value: 1_000_000_000_000 };
    const service = serviceAt(now);
    expect(() => service.checkHoneypot(undefined)).not.toThrow();
    expect(() => service.checkHoneypot(null)).not.toThrow();
    expect(() => service.checkHoneypot('')).not.toThrow();
    expect(() => service.checkHoneypot('   ')).not.toThrow();
  });

  it('rejects a filled honeypot', () => {
    const now = { value: 1_000_000_000_000 };
    const service = serviceAt(now);
    expect(reasonOf(() => service.checkHoneypot('http://spam.example'))).toBe('honeypot_filled');
    expect(reasonOf(() => service.checkHoneypot(42))).toBe('honeypot_filled');
  });
});
