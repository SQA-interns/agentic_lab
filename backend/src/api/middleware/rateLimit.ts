/**
 * Rate limiting (specification § 11).
 *
 * Per-IP limits sized for human use: a participant registers once, an organizer exports
 * occasionally. The registration limit is the main brake on scripted submissions that
 * clear the other anti-automation controls (AC-G-10).
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

export function registrationRateLimiter(config: AppConfig): RateLimitRequestHandler {
  return limiter(
    config.rateLimit.registrationWindowMs,
    config.rateLimit.registrationMax,
    'RATE_LIMITED',
    'Too many registration attempts from this address. Please try again later.',
  );
}

export function configRateLimiter(): RateLimitRequestHandler {
  return limiter(
    5 * 60_000,
    60,
    'RATE_LIMITED',
    'Too many requests. Please try again later.',
  );
}

export function exportRateLimiter(): RateLimitRequestHandler {
  return limiter(
    15 * 60_000,
    10,
    'RATE_LIMITED',
    'Too many export requests. Please try again later.',
  );
}

export function globalRateLimiter(): RateLimitRequestHandler {
  return limiter(15 * 60_000, 300, 'RATE_LIMITED', 'Too many requests. Please try again later.');
}
