/**
 * Central error mapping (specification § 4.3, § 10).
 *
 * The only place that turns an error into an HTTP response. Anything not in the known
 * taxonomy becomes a generic 500: stack traces, SQL text and file paths stay in the
 * server log and never reach a client (AC-G-05, AC-G-11).
 */
import type { ErrorRequestHandler, RequestHandler } from 'express';

import {
  AntiAutomationError,
  NotFoundError,
  ValidationError,
  isAppError,
} from '../../domain/errors.js';
import type { Logger } from '../../infrastructure/logging/logger.js';

export interface ErrorBody {
  error: { code: string; message: string; fields?: unknown };
  requestId: string;
}

export function notFoundHandler(): RequestHandler {
  return (_req, _res, next) => {
    next(new NotFoundError());
  };
}

export function errorHandler(logger: Logger): ErrorRequestHandler {
  return (error, req, res, _next) => {
    const requestId = String(res.getHeader('X-Request-Id') ?? '');

    // Body-parser failures arrive as generic errors; map them onto the documented
    // contract instead of letting them surface as a 500.
    if (isBodyParserError(error)) {
      const mapped = mapBodyParserError(error);
      res.status(mapped.status).json({
        error: { code: mapped.code, message: mapped.message },
        requestId,
      } satisfies ErrorBody);
      return;
    }

    if (error instanceof ValidationError) {
      res.status(error.httpStatus).json({
        error: { code: error.code, message: error.message, fields: error.fields },
        requestId,
      } satisfies ErrorBody);
      return;
    }

    if (error instanceof AntiAutomationError) {
      // The reason is logged, never returned: an automated client must not learn which
      // control rejected it (specification § 11).
      logger.warn({ requestId, reason: error.reason, path: req.path }, 'anti-automation rejection');
      res
        .status(error.httpStatus)
        .json({ error: { code: error.code, message: error.message }, requestId } satisfies ErrorBody);
      return;
    }

    if (isAppError(error) && error.httpStatus < 500) {
      res
        .status(error.httpStatus)
        .json({ error: { code: error.code, message: error.message }, requestId } satisfies ErrorBody);
      return;
    }

    logger.error({ requestId, path: req.path, err: error }, 'unhandled error');
    res.status(500).json({
      error: {
        code: 'INTERNAL_ERROR',
        message: 'The request could not be processed. Please try again later.',
      },
      requestId,
    } satisfies ErrorBody);
  };
}

function mapBodyParserError(error: BodyParserError): {
  status: number;
  code: string;
  message: string;
} {
  switch (error.status) {
    case 413:
      return { status: 413, code: 'PAYLOAD_TOO_LARGE', message: 'The request body is too large.' };
    case 415:
      return {
        status: 415,
        code: 'UNSUPPORTED_MEDIA_TYPE',
        message: 'The request body must be JSON.',
      };
    default:
      return {
        status: 400,
        code: 'MALFORMED_REQUEST',
        message: 'The request body must be a JSON object.',
      };
  }
}

interface BodyParserError extends Error {
  status?: number;
  type?: string;
}

function isBodyParserError(error: unknown): error is BodyParserError {
  if (!(error instanceof Error)) {
    return false;
  }
  const candidate = error as BodyParserError;
  return (
    typeof candidate.type === 'string' &&
    ['entity.parse.failed', 'entity.too.large', 'encoding.unsupported', 'charset.unsupported'].includes(
      candidate.type,
    )
  );
}
