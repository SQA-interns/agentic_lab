/**
 * Anti-automation form tokens (specification § 11, ADR-005).
 *
 * The form endpoint issues a signed, short-lived token; the registration endpoint
 * accepts a submission only if that token is authentic, fresh, not too fresh, bound to
 * the same variant, and not already used. Together with the honeypot and the per-IP rate
 * limit this raises the cost of scripted submissions without sending participant data to
 * a third-party CAPTCHA service (AC-G-10).
 */
import { createHmac, randomBytes, timingSafeEqual } from 'node:crypto';

import { AntiAutomationError } from '../domain/errors.js';
import type { RegistrationVariant } from '../domain/registration.js';

interface TokenPayload {
  readonly nonce: string;
  readonly variant: RegistrationVariant;
  readonly issuedAt: number;
}

export interface FormTokenServiceOptions {
  readonly secret: string;
  readonly ttlSeconds: number;
  readonly minFillSeconds: number;
  /** Injectable clock; production uses `Date.now`. */
  readonly now?: () => number;
}

function base64UrlEncode(value: Buffer | string): string {
  return Buffer.from(value).toString('base64url');
}

export class FormTokenService {
  private readonly secret: string;
  private readonly ttlSeconds: number;
  private readonly minFillSeconds: number;
  private readonly now: () => number;
  /**
   * Nonces already redeemed, with the time they expire.
   *
   * A token buys exactly one registration; replaying a captured token is rejected. The
   * map is pruned on every consume, so it stays bounded by the number of tokens issued
   * within one TTL. This is per-process state: running several backend instances would
   * weaken the single-use guarantee until a shared store replaces it (documented
   * limitation in specification § 11).
   */
  private readonly usedNonces = new Map<string, number>();

  constructor(options: FormTokenServiceOptions) {
    this.secret = options.secret;
    this.ttlSeconds = options.ttlSeconds;
    this.minFillSeconds = options.minFillSeconds;
    this.now = options.now ?? (() => Date.now());
  }

  issue(variant: RegistrationVariant): string {
    const payload: TokenPayload = {
      nonce: randomBytes(16).toString('base64url'),
      variant,
      issuedAt: Math.floor(this.now() / 1000),
    };
    const encoded = base64UrlEncode(JSON.stringify(payload));
    return `${encoded}.${this.sign(encoded)}`;
  }

  /**
   * Verify and consume a token.
   *
   * @throws AntiAutomationError with an internal reason; the HTTP layer returns the same
   * opaque message for every reason so a bot cannot learn which check it failed.
   */
  consume(token: string, variant: RegistrationVariant): void {
    const separator = token.indexOf('.');
    if (separator <= 0) {
      throw new AntiAutomationError('token_malformed');
    }

    const encoded = token.slice(0, separator);
    const signature = token.slice(separator + 1);
    if (!this.verifySignature(encoded, signature)) {
      throw new AntiAutomationError('token_signature_invalid');
    }

    let payload: TokenPayload;
    try {
      payload = JSON.parse(Buffer.from(encoded, 'base64url').toString('utf8')) as TokenPayload;
    } catch {
      throw new AntiAutomationError('token_payload_invalid');
    }

    if (typeof payload.nonce !== 'string' || typeof payload.issuedAt !== 'number') {
      throw new AntiAutomationError('token_payload_invalid');
    }
    if (payload.variant !== variant) {
      throw new AntiAutomationError('token_variant_mismatch');
    }

    const ageSeconds = Math.floor(this.now() / 1000) - payload.issuedAt;
    if (ageSeconds > this.ttlSeconds) {
      throw new AntiAutomationError('token_expired');
    }
    if (ageSeconds < this.minFillSeconds) {
      throw new AntiAutomationError('submitted_too_fast');
    }

    this.pruneExpired();
    if (this.usedNonces.has(payload.nonce)) {
      throw new AntiAutomationError('token_replayed');
    }
    this.usedNonces.set(payload.nonce, (payload.issuedAt + this.ttlSeconds) * 1000);
  }

  /** The honeypot field must be absent or empty; any content means a script filled it. */
  checkHoneypot(value: unknown): void {
    if (value === undefined || value === null) {
      return;
    }
    if (typeof value !== 'string' || value.trim().length > 0) {
      throw new AntiAutomationError('honeypot_filled');
    }
  }

  private sign(encodedPayload: string): string {
    return createHmac('sha256', this.secret).update(encodedPayload).digest('base64url');
  }

  private verifySignature(encodedPayload: string, signature: string): boolean {
    const expected = Buffer.from(this.sign(encodedPayload));
    const provided = Buffer.from(signature);
    // Length must match before timingSafeEqual, which throws on differing lengths.
    return expected.length === provided.length && timingSafeEqual(expected, provided);
  }

  private pruneExpired(): void {
    const now = this.now();
    for (const [nonce, expiresAt] of this.usedNonces) {
      if (expiresAt <= now) {
        this.usedNonces.delete(nonce);
      }
    }
  }
}
