/**
 * Structured logging (specification § 11).
 *
 * Personal data is redacted at the logger so that no code path can leak participant
 * details into the log stream by accident (AC-G-11). Application logs identify a
 * registration by its reference, never by the participant's email or name.
 */
import { pino, type Logger } from 'pino';

/** Paths redacted wherever they appear in a log record. */
const REDACTED_PATHS = [
  'req.headers.authorization',
  'req.headers.cookie',
  'req.body.email',
  'req.body.firstName',
  'req.body.lastName',
  'req.body.organization',
  'req.body.studyInstitution',
  'req.body.studyProgramme',
  'req.body.studentId',
  'body.email',
  'body.firstName',
  'body.lastName',
  'email',
  'firstName',
  'lastName',
  'studentId',
  'password',
  'smtpPass',
];

export function createLogger(level: string, isProduction: boolean): Logger {
  return pino({
    level,
    redact: { paths: REDACTED_PATHS, censor: '[redacted]' },
    base: undefined,
    timestamp: pino.stdTimeFunctions.isoTime,
    ...(isProduction ? {} : { transport: undefined }),
  });
}

export type { Logger };
