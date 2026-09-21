/**
 * Test harness: builds a real application instance on a temporary data directory.
 *
 * Nothing is stubbed except the clock and the mail transport, so API, integration and
 * acceptance tests exercise the production wiring — the same Express app, the same SQLite
 * schema, the same JSON backup store.
 */
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';

import { pino } from 'pino';

import { createApplication, type Application } from '../../src/api/app.js';
import { loadConfig, type AppConfig } from '../../src/config/env.js';
import { parseOptionsConfig, type OptionCatalogue } from '../../src/config/optionsConfig.js';
import type { MailPort, OutgoingMail } from '../../src/application/ports.js';

export const TEST_SECRET = 'test-form-token-secret-value-long-enough';

/** Mail port that records every message instead of sending it. */
export class RecordingMailPort implements MailPort {
  readonly sent: OutgoingMail[] = [];
  /** When set, `send` rejects with this error; used for the email-failure tests. */
  failWith: Error | null = null;

  send(message: OutgoingMail): Promise<void> {
    if (this.failWith !== null) {
      return Promise.reject(this.failWith);
    }
    this.sent.push(message);
    return Promise.resolve();
  }
}

export const DEFAULT_OPTIONS_CONFIG = {
  conferenceName: 'Test Conference',
  privacyConsentText: 'I agree to the processing of my personal data.',
  groups: [
    {
      id: 'workshops',
      displayName: 'Workshops',
      options: [
        { id: 'workshop-ai', displayName: 'Workshop: AI', active: true, availableTo: ['external', 'student'] },
        { id: 'workshop-external-only', displayName: 'Round table', active: true, availableTo: ['external'] },
        { id: 'workshop-retired', displayName: 'Retired workshop', active: false, availableTo: ['external', 'student'] },
      ],
    },
    {
      id: 'events',
      displayName: 'Events',
      options: [
        { id: 'event-opening', displayName: 'Opening ceremony', active: true, availableTo: ['external', 'student'] },
      ],
    },
    {
      id: 'meals',
      displayName: 'Meals',
      options: [
        { id: 'meal-lunch', displayName: 'Lunch', active: true, availableTo: ['external', 'student'] },
      ],
    },
    { id: 'other', displayName: 'Other activities', options: [] },
  ],
};

export interface TestApplication extends Application {
  readonly mailPort: RecordingMailPort;
  readonly dataDir: string;
  dispose(): void;
}

export interface TestApplicationOptions {
  readonly optionsConfig?: unknown;
  readonly env?: Record<string, string>;
  readonly now?: () => Date;
  /** Reuse an existing data directory, e.g. to simulate a restart. */
  readonly dataDir?: string;
}

export function buildTestConfig(options: TestApplicationOptions = {}): AppConfig {
  const dataDir = options.dataDir ?? mkdtempSync(path.join(tmpdir(), 'conf-test-'));
  return loadConfig({
    NODE_ENV: 'test',
    DATA_DIR: dataDir,
    FORM_TOKEN_SECRET: TEST_SECRET,
    // Zero minimum fill time keeps tests fast; the timing rule itself is unit-tested.
    FORM_MIN_FILL_SECONDS: '0',
    MAIL_TRANSPORT: 'json',
    MAIL_FROM: 'conference@example.org',
    ORGANIZER_EMAILS: 'organizer-a@example.org,organizer-b@example.org',
    EXPORT_USERNAME: 'organizer',
    EXPORT_PASSWORD: 'organizer-password',
    CORS_ALLOWED_ORIGINS: 'http://localhost:8080',
    LOG_LEVEL: 'silent',
    ...options.env,
  });
}

export function createTestApplication(options: TestApplicationOptions = {}): TestApplication {
  const config = buildTestConfig(options);
  const catalogue: OptionCatalogue = parseOptionsConfig(
    options.optionsConfig ?? DEFAULT_OPTIONS_CONFIG,
    'test options',
  );
  const mailPort = new RecordingMailPort();
  const application = createApplication(config, {
    catalogue,
    mail: mailPort,
    logger: pino({ level: 'silent' }),
    ...(options.now ? { now: options.now } : {}),
  });

  return {
    ...application,
    mailPort,
    dataDir: config.dataDir,
    dispose(): void {
      application.close();
      if (options.dataDir === undefined) {
        rmSync(config.dataDir, { recursive: true, force: true });
      }
    },
  };
}
