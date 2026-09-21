/**
 * Rate limiting (specification § 11).
 *
 * The limits are asymmetric on purpose. Creating a registration is the only action worth
 * abusing, so the write endpoint is strict. The read-only endpoints must stay generous,
 * because many legitimate participants share one public address behind NAT — a
 * university, a company or conference wifi — and a limit tight enough to be interesting
 * to an attacker would deny the form to everyone behind that address during a
 * registration rush. Denying the form is a worse outcome than serving one extra copy of
 * a public configuration document.
 */
import rateLimit, { type RateLimitRequestHandler } from 'express-rate-limit';

import type { AppConfig } from '../../config/env.js';

function limiter(windowMs: number, max: number, code: string, message: string): RateLimitRequestHandler {
  return rateLimit({
    windowMs,
    limit: max,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    handler: (_req, res) => {
      res.status(429).json({
        error: { code, message },
        requestId: String(res.getHeader('X-Request-Id') ?? ''),
      });
    },
  });
}

/** The real anti-abuse control: submissions per address (AC-G-10). */
export function registrationRateLimiter(config: AppConfig): RateLimitRequestHandler {
  return limiter(
    config.rateLimit.registrationWindowMs,
    config.rateLimit.registrationMax,
    'RATE_LIMITED',
    'Too many registration attempts from this address. Please try again later.',
  );
}

/**
 * Form configuration reads.
 *
 * One page load costs one request here, so this limit is effectively a cap on how many
 * people behind a shared address may open the form. It is a backstop against scripted
 * token farming only; the registration limit above is what actually caps submissions.
 */
export function configRateLimiter(config: AppConfig): RateLimitRequestHandler {
  return limiter(
    config.rateLimit.configWindowMs,
    config.rateLimit.configMax,
    'RATE_LIMITED',
    'Too many requests. Please try again later.',
  );
}

/** Organizer-only endpoint: a human downloading a spreadsheet occasionally. */
export function exportRateLimiter(): RateLimitRequestHandler {
  return limiter(
    15 * 60_000,
    10,
    'RATE_LIMITED',
    'Too many export requests. Please try again later.',
  );
}

/** Blanket backstop across the whole API, sized for the shared-address case. */
export function globalRateLimiter(config: AppConfig): RateLimitRequestHandler {
  return limiter(
    config.rateLimit.globalWindowMs,
    config.rateLimit.globalMax,
    'RATE_LIMITED',
    'Too many requests. Please try again later.',
  );
}
