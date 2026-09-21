/**
 * Error taxonomy for the application (specification § 10).
 *
 * Domain and application code throws these typed errors; the single Express error
 * handler is the only place that maps them onto the public HTTP error envelope.
 */

/** One violated rule, addressed to a single request property. */
export interface FieldError {
  /** Request property name the participant can act on. */
  readonly field: string;
  /** Stable machine-readable rule identifier. */
  readonly code: string;
  /** Human-readable message, free of implementation detail. */
  readonly message: string;
}

export abstract class AppError extends Error {
  abstract readonly httpStatus: number;
  abstract readonly code: string;

  protected constructor(message: string) {
    super(message);
    this.name = new.target.name;
  }
}

/** The submission violated one or more input rules (specification § 5). */
export class ValidationError extends AppError {
  readonly httpStatus = 400;
  readonly code = 'VALIDATION_ERROR';
  readonly fields: readonly FieldError[];

  constructor(fields: readonly FieldError[], message?: string) {
    super(message ?? 'The registration could not be accepted because some fields are invalid.');
    this.fields = fields;
  }
}

/** The request body was absent, not JSON, or not a JSON object. */
export class MalformedRequestError extends AppError {
  readonly httpStatus = 400;
  readonly code = 'MALFORMED_REQUEST';

  constructor(message = 'The request body must be a JSON object.') {
    super(message);
  }
}

/**
 * An anti-automation check failed (specification § 11).
 *
 * The message is deliberately identical for every failing check so that an automated
 * client learns nothing about which control rejected it.
 */
export class AntiAutomationError extends AppError {
  readonly httpStatus = 400;
  readonly code = 'ANTI_AUTOMATION_FAILED';
  /** Which check failed. Logged server-side only, never returned to the client. */
  readonly reason: string;

  constructor(reason: string) {
    super('Your submission could not be verified. Please reload the form and try again.');
    this.reason = reason;
  }
}

/** Export credentials missing or wrong. */
export class UnauthorizedError extends AppError {
  readonly httpStatus = 401;
  readonly code = 'UNAUTHORIZED';

  constructor(message = 'Valid credentials are required.') {
    super(message);
  }
}

export class NotFoundError extends AppError {
  readonly httpStatus = 404;
  readonly code = 'NOT_FOUND';

  constructor(message = 'The requested resource does not exist.') {
    super(message);
  }
}

/** Invalid environment or conference-options configuration; aborts startup. */
export class ConfigurationError extends AppError {
  readonly httpStatus = 500;
  readonly code = 'CONFIGURATION_ERROR';

  constructor(message: string) {
    super(message);
  }
}

/** The registration could not be stored durably; nothing partial remains. */
export class PersistenceError extends AppError {
  readonly httpStatus = 500;
  readonly code = 'INTERNAL_ERROR';

  constructor(message: string, options?: { cause?: unknown }) {
    super(message);
    if (options?.cause !== undefined) {
      this.cause = options.cause;
    }
  }
}

export function isAppError(error: unknown): error is AppError {
  return error instanceof AppError;
}
