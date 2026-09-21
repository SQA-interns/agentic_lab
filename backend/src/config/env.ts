/**
 * Environment configuration (specification § 12).
 *
 * Every environment-specific value is read here, validated, and exposed as a frozen
 * typed object. A missing or invalid required value aborts startup with a message that
 * names the variable, so a misconfigured deployment fails immediately and visibly rather
 * than at the first registration (AC-G-15).
 */
import path from 'node:path';

import { z } from 'zod';

import { ConfigurationError } from '../domain/errors.js';
import { formatIssues } from './formatIssues.js';

const booleanFromString = z
  .union([z.boolean(), z.enum(['true', 'false', '1', '0'])])
  .transform((value) => value === true || value === 'true' || value === '1');

const csvList = z
  .string()
  .transform((value) =>
    value
      .split(',')
      .map((entry) => entry.trim())
      .filter((entry) => entry.length > 0),
  );

const envSchema = z
  .object({
    NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
    PORT: z.coerce.number().int().min(1).max(65535).default(3000),

    DATA_DIR: z.string().min(1).default('./data'),
    DATABASE_FILE: z.string().min(1).optional(),
    OPTIONS_CONFIG_FILE: z.string().min(1).default('./config/conference-options.json'),

    FORM_TOKEN_SECRET: z.string().min(32, 'must be at least 32 characters'),
    FORM_TOKEN_TTL_SECONDS: z.coerce.number().int().min(60).max(86400).default(1800),
    FORM_MIN_FILL_SECONDS: z.coerce.number().int().min(0).max(120).default(3),

    CORS_ALLOWED_ORIGINS: csvList.default(['http://localhost:8080']),
    MAX_BODY_BYTES: z.coerce.number().int().min(1024).max(1048576).default(32768),
    TRUST_PROXY: booleanFromString.default(false),

    MAIL_TRANSPORT: z.enum(['smtp', 'json']).default('json'),
    SMTP_HOST: z.string().min(1).optional(),
    SMTP_PORT: z.coerce.number().int().min(1).max(65535).default(587),
    SMTP_SECURE: booleanFromString.default(false),
    SMTP_USER: z.string().optional(),
    SMTP_PASS: z.string().optional(),
    MAIL_FROM: z.string().min(3),
    ORGANIZER_EMAILS: csvList.refine((list) => list.length > 0, 'must list at least one address'),

    EXPORT_USERNAME: z.string().min(1),
    EXPORT_PASSWORD: z.string().min(12, 'must be at least 12 characters'),

    CONFERENCE_NAME: z.string().min(1).optional(),
    LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace', 'silent']).default('info'),

    RATE_LIMIT_REGISTRATION_MAX: z.coerce.number().int().min(1).default(5),
    RATE_LIMIT_REGISTRATION_WINDOW_MINUTES: z.coerce.number().int().min(1).default(10),
  })
  .superRefine((env, ctx) => {
    if (env.MAIL_TRANSPORT === 'smtp' && !env.SMTP_HOST) {
      ctx.addIssue({
        code: 'custom',
        path: ['SMTP_HOST'],
        message: 'is required when MAIL_TRANSPORT=smtp',
      });
    }
    if (env.NODE_ENV === 'production' && env.MAIL_TRANSPORT !== 'smtp') {
      ctx.addIssue({
        code: 'custom',
        path: ['MAIL_TRANSPORT'],
        message: 'must be "smtp" in production; "json" does not deliver mail',
      });
    }
  });

export interface AppConfig {
  readonly nodeEnv: 'development' | 'test' | 'production';
  readonly isProduction: boolean;
  readonly port: number;
  readonly dataDir: string;
  readonly databaseFile: string;
  readonly backupDir: string;
  readonly optionsConfigFile: string;
  readonly formToken: {
    readonly secret: string;
    readonly ttlSeconds: number;
    readonly minFillSeconds: number;
  };
  readonly corsAllowedOrigins: readonly string[];
  readonly maxBodyBytes: number;
  readonly trustProxy: boolean;
  readonly mail: {
    readonly transport: 'smtp' | 'json';
    readonly host: string | undefined;
    readonly port: number;
    readonly secure: boolean;
    readonly user: string | undefined;
    readonly pass: string | undefined;
    readonly from: string;
    readonly organizerRecipients: readonly string[];
  };
  readonly export: { readonly username: string; readonly password: string };
  readonly conferenceNameOverride: string | undefined;
  readonly logLevel: string;
  readonly rateLimit: {
    readonly registrationMax: number;
    readonly registrationWindowMs: number;
  };
}

/**
 * Parse `process.env` (or an explicit source) into the application configuration.
 *
 * @throws ConfigurationError listing every invalid or missing variable.
 */
export function loadConfig(source: NodeJS.ProcessEnv = process.env): AppConfig {
  const parsed = envSchema.safeParse(source);
  if (!parsed.success) {
    const details = formatIssues(parsed.error.issues);
    throw new ConfigurationError(`Invalid environment configuration:\n${details}`);
  }

  const env = parsed.data;
  const dataDir = path.resolve(env.DATA_DIR);

  return Object.freeze({
    nodeEnv: env.NODE_ENV,
    isProduction: env.NODE_ENV === 'production',
    port: env.PORT,
    dataDir,
    databaseFile: env.DATABASE_FILE
      ? path.resolve(env.DATABASE_FILE)
      : path.join(dataDir, 'registrations.db'),
    backupDir: path.join(dataDir, 'registrations'),
    optionsConfigFile: path.resolve(env.OPTIONS_CONFIG_FILE),
    formToken: Object.freeze({
      secret: env.FORM_TOKEN_SECRET,
      ttlSeconds: env.FORM_TOKEN_TTL_SECONDS,
      minFillSeconds: env.FORM_MIN_FILL_SECONDS,
    }),
    corsAllowedOrigins: Object.freeze(env.CORS_ALLOWED_ORIGINS),
    maxBodyBytes: env.MAX_BODY_BYTES,
    trustProxy: env.TRUST_PROXY,
    mail: Object.freeze({
      transport: env.MAIL_TRANSPORT,
      host: env.SMTP_HOST,
      port: env.SMTP_PORT,
      secure: env.SMTP_SECURE,
      user: env.SMTP_USER,
      pass: env.SMTP_PASS,
      from: env.MAIL_FROM,
      organizerRecipients: Object.freeze(env.ORGANIZER_EMAILS),
    }),
    export: Object.freeze({ username: env.EXPORT_USERNAME, password: env.EXPORT_PASSWORD }),
    conferenceNameOverride: env.CONFERENCE_NAME,
    logLevel: env.LOG_LEVEL,
    rateLimit: Object.freeze({
      registrationMax: env.RATE_LIMIT_REGISTRATION_MAX,
      registrationWindowMs: env.RATE_LIMIT_REGISTRATION_WINDOW_MINUTES * 60_000,
    }),
  });
}
