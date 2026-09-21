/**
 * Preventive security middleware (specification § 11).
 */
import { randomUUID } from 'node:crypto';

import cors from 'cors';
import type { RequestHandler } from 'express';
import helmet from 'helmet';

import type { AppConfig } from '../../config/env.js';

/**
 * Attach a request id to every response.
 *
 * Error responses carry it so an operator can find the corresponding server-side log
 * entry without the response having to contain any internal detail.
 */
export function requestId(): RequestHandler {
  return (req, res, next) => {
    const incoming = req.header('X-Request-Id');
    const id = incoming && /^[A-Za-z0-9-]{1,64}$/u.test(incoming) ? incoming : randomUUID();
    res.setHeader('X-Request-Id', id);
    next();
  };
}

/**
 * Security response headers.
 *
 * The API serves JSON only, so its own CSP can forbid everything; the frontend's CSP is
 * configured in nginx where the documents are served.
 */
export function securityHeaders(config: AppConfig): RequestHandler {
  return helmet({
    contentSecurityPolicy: {
      useDefaults: false,
      directives: {
        'default-src': ["'none'"],
        'frame-ancestors': ["'none'"],
        'base-uri': ["'none'"],
        'form-action': ["'none'"],
      },
    },
    crossOriginResourcePolicy: { policy: 'same-site' },
    referrerPolicy: { policy: 'no-referrer' },
    frameguard: { action: 'deny' },
    hsts: config.isProduction ? { maxAge: 31536000, includeSubDomains: true } : false,
  });
}

/**
 * Cross-origin policy.
 *
 * An explicit origin allowlist with credentials disabled: the API is called by the
 * conference frontend only, and no browser-held credential is involved, so there is no
 * reason to accept arbitrary origins.
 */
export function corsPolicy(config: AppConfig): RequestHandler {
  const allowed = new Set(config.corsAllowedOrigins);
  return cors({
    origin(origin, callback) {
      // A request without an Origin header is not a cross-origin browser request
      // (curl, server-to-server, same-origin navigation) and is left to the other
      // controls — CORS exists to protect browsers, not to authenticate callers.
      if (origin === undefined || allowed.has(origin)) {
        callback(null, true);
        return;
      }
      callback(null, false);
    },
    credentials: false,
    methods: ['GET', 'POST', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-Request-Id'],
    maxAge: 600,
  });
}
