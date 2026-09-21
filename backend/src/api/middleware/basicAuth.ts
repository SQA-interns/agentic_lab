/**
 * HTTP Basic authentication for the organizer export (specification § 4.4, AC-008-07).
 */
import { createHash, timingSafeEqual } from 'node:crypto';

import type { RequestHandler } from 'express';

import { UnauthorizedError } from '../../domain/errors.js';

/**
 * Compare two secrets without leaking their contents through timing.
 *
 * Hashing first gives both operands a fixed length, so the comparison reveals nothing
 * about the expected credential's length either.
 */
function secretsMatch(provided: string, expected: string): boolean {
  const a = createHash('sha256').update(provided).digest();
  const b = createHash('sha256').update(expected).digest();
  return timingSafeEqual(a, b);
}

export function basicAuth(username: string, password: string, realm: string): RequestHandler {
  return (req, res, next) => {
    const header = req.header('Authorization');
    if (header === undefined || !header.startsWith('Basic ')) {
      res.setHeader('WWW-Authenticate', `Basic realm="${realm}", charset="UTF-8"`);
      next(new UnauthorizedError());
      return;
    }

    const decoded = Buffer.from(header.slice('Basic '.length), 'base64').toString('utf8');
    const separator = decoded.indexOf(':');
    const providedUser = separator >= 0 ? decoded.slice(0, separator) : '';
    const providedPassword = separator >= 0 ? decoded.slice(separator + 1) : '';

    // Both comparisons always run so that a wrong username and a wrong password take the
    // same time.
    const userMatches = secretsMatch(providedUser, username);
    const passwordMatches = secretsMatch(providedPassword, password);
    if (!userMatches || !passwordMatches) {
      res.setHeader('WWW-Authenticate', `Basic realm="${realm}", charset="UTF-8"`);
      next(new UnauthorizedError());
      return;
    }

    next();
  };
}
