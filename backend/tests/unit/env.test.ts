/**
 * Unit tests — environment configuration (specification § 12, AC-G-15).
 *
 * Level justification: the startup contract is "a misconfigured deployment must not
 * start". That is a pure parsing concern, and testing it directly lets every required
 * variable be checked without launching a process per case.
 */
import { describe, expect, it } from 'vitest';

import { loadConfig } from '../../src/config/env.js';
import { ConfigurationError } from '../../src/domain/errors.js';

const minimal = {
  FORM_TOKEN_SECRET: 'a-secret-value-that-is-long-enough-32x',
  MAIL_FROM: 'conference@example.org',
  ORGANIZER_EMAILS: 'organizer@example.org',
  EXPORT_USERNAME: 'organizer',
  EXPORT_PASSWORD: 'organizer-password',
};

describe('loadConfig', () => {
  it('applies documented defaults when optional values are absent', () => {
    const config = loadConfig(minimal);
    expect(config.port).toBe(3000);
    expect(config.formToken.ttlSeconds).toBe(1800);
    expect(config.formToken.minFillSeconds).toBe(3);
    expect(config.maxBodyBytes).toBe(32768);
    expect(config.trustProxy).toBe(false);
    expect(config.corsAllowedOrigins).toEqual(['http://localhost:8080']);
  });

  it('defaults the read limits far above the write limit, for shared public addresses', () => {
    const config = loadConfig(minimal);
    expect(config.rateLimit.registrationMax).toBe(5);
    expect(config.rateLimit.configMax).toBe(300);
    expect(config.rateLimit.globalMax).toBe(1200);
    expect(config.rateLimit.configMax).toBeGreaterThan(config.rateLimit.registrationMax);
    expect(config.rateLimit.globalMax).toBeGreaterThan(config.rateLimit.configMax);
  });

  it('allows every rate limit to be tuned per deployment', () => {
    const config = loadConfig({
      ...minimal,
      RATE_LIMIT_CONFIG_MAX: '42',
      RATE_LIMIT_CONFIG_WINDOW_MINUTES: '2',
      RATE_LIMIT_GLOBAL_MAX: '99',
      RATE_LIMIT_GLOBAL_WINDOW_MINUTES: '3',
    });
    expect(config.rateLimit.configMax).toBe(42);
    expect(config.rateLimit.configWindowMs).toBe(120_000);
    expect(config.rateLimit.globalMax).toBe(99);
    expect(config.rateLimit.globalWindowMs).toBe(180_000);
  });

  it('derives the database file and backup directory from the data directory', () => {
    const config = loadConfig({ ...minimal, DATA_DIR: './tmp-data' });
    expect(config.databaseFile.endsWith('registrations.db')).toBe(true);
    expect(config.backupDir.endsWith('registrations')).toBe(true);
  });

  it('splits comma-separated organizer recipients (AC-007-06)', () => {
    const config = loadConfig({ ...minimal, ORGANIZER_EMAILS: ' a@x.org , b@x.org ' });
    expect(config.mail.organizerRecipients).toEqual(['a@x.org', 'b@x.org']);
  });

  it.each([['FORM_TOKEN_SECRET'], ['MAIL_FROM'], ['ORGANIZER_EMAILS'], ['EXPORT_USERNAME'], ['EXPORT_PASSWORD']])(
    'refuses to start when %s is missing, naming the variable',
    (variable) => {
      const env = { ...minimal } as Record<string, string>;
      delete env[variable];
      expect(() => loadConfig(env as NodeJS.ProcessEnv)).toThrow(new RegExp(variable, 'u'));
    },
  );

  it('rejects a form-token secret that is too short to be a useful HMAC key', () => {
    expect(() => loadConfig({ ...minimal, FORM_TOKEN_SECRET: 'short' })).toThrow(
      ConfigurationError,
    );
  });

  it('rejects an export password below the minimum length', () => {
    expect(() => loadConfig({ ...minimal, EXPORT_PASSWORD: 'short' })).toThrow(
      /EXPORT_PASSWORD/u,
    );
  });

  it('rejects the non-delivering mail transport in production', () => {
    expect(() =>
      loadConfig({ ...minimal, NODE_ENV: 'production', MAIL_TRANSPORT: 'json' }),
    ).toThrow(/MAIL_TRANSPORT/u);
  });

  it('requires an SMTP host when SMTP delivery is selected', () => {
    expect(() => loadConfig({ ...minimal, MAIL_TRANSPORT: 'smtp' })).toThrow(/SMTP_HOST/u);
  });

  it('rejects an out-of-range port', () => {
    expect(() => loadConfig({ ...minimal, PORT: '70000' })).toThrow(/PORT/u);
  });

  it('returns a frozen object so configuration cannot be mutated at runtime', () => {
    const config = loadConfig(minimal);
    expect(Object.isFrozen(config)).toBe(true);
  });
});
