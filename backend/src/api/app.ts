/**
 * Express application composition (specification § 2.2, § 4).
 *
 * Wires configuration, infrastructure adapters, use cases and routes together. Kept
 * separate from `server.ts` so tests can drive the same application without opening a
 * port.
 */
import express, { type Express } from 'express';
import type { Database } from 'better-sqlite3';

import { ExportService } from '../application/exportService.js';
import { FormTokenService } from '../application/formTokenService.js';
import { RegistrationService } from '../application/registrationService.js';
import type { MailPort } from '../domain/ports/mailPort.js';
import type { AppConfig } from '../config/env.js';
import { loadOptionsConfig, type OptionCatalogue } from '../config/optionsConfig.js';
import { JsonBackupStore } from '../infrastructure/backup/jsonBackupStore.js';
import { openDatabase } from '../infrastructure/db/database.js';
import { RegistrationRepository } from '../infrastructure/db/registrationRepository.js';
import { createLogger, type Logger } from '../infrastructure/logging/logger.js';
import { NodemailerMailAdapter } from '../infrastructure/mail/mailer.js';
import { errorHandler, notFoundHandler } from './middleware/errorHandler.js';
import { configRateLimiter, globalRateLimiter, registrationRateLimiter } from './middleware/rateLimit.js';
import { corsPolicy, requestId, securityHeaders } from './middleware/security.js';
import { exportRouter } from './routes/export.js';
import { healthRouter } from './routes/health.js';
import { registrationConfigRouter } from './routes/registrationConfig.js';
import { registrationsRouter } from './routes/registrations.js';

export interface ApplicationOverrides {
  readonly logger?: Logger;
  readonly mail?: MailPort;
  readonly database?: Database;
  readonly catalogue?: OptionCatalogue;
  readonly now?: () => Date;
}

export interface Application {
  readonly app: Express;
  readonly config: AppConfig;
  readonly logger: Logger;
  readonly database: Database;
  readonly repository: RegistrationRepository;
  readonly backupStore: JsonBackupStore;
  readonly catalogue: OptionCatalogue;
  readonly mail: MailPort;
  close(): void;
}

export function createApplication(config: AppConfig, overrides: ApplicationOverrides = {}): Application {
  const logger = overrides.logger ?? createLogger(config.logLevel, config.isProduction);
  const catalogue = overrides.catalogue ?? loadOptionsConfig(config.optionsConfigFile);
  const database = overrides.database ?? openDatabase({ file: config.databaseFile });
  const repository = new RegistrationRepository(database);
  const backupStore = new JsonBackupStore(config.backupDir);
  const mail = overrides.mail ?? new NodemailerMailAdapter(config);
  const conferenceName = config.conferenceNameOverride ?? catalogue.conferenceName;

  const formTokens = new FormTokenService({
    secret: config.formToken.secret,
    ttlSeconds: config.formToken.ttlSeconds,
    minFillSeconds: config.formToken.minFillSeconds,
  });

  const registrationService = new RegistrationService({
    repository,
    backupStore,
    catalogue,
    formTokens,
    mail,
    organizerRecipients: config.mail.organizerRecipients,
    logger,
    ...(overrides.now ? { now: overrides.now } : {}),
  });

  const exportService = new ExportService(repository, overrides.now ?? (() => new Date()));

  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', config.trustProxy);

  app.use(requestId());
  app.use(securityHeaders(config));
  app.use(corsPolicy(config));
  app.use(globalRateLimiter());
  app.use(express.json({ limit: config.maxBodyBytes, type: 'application/json' }));

  // Each limiter is mounted on its own exact path, so the strict registration limit
  // cannot accidentally throttle unrelated endpoints.
  app.use('/api/registration-config', configRateLimiter());
  app.use('/api/registrations', registrationRateLimiter(config));

  app.use('/api', healthRouter());
  app.use(
    '/api',
    registrationConfigRouter({
      catalogue,
      formTokens,
      formTokenTtlSeconds: config.formToken.ttlSeconds,
      conferenceName,
    }),
  );
  app.use('/api', registrationsRouter({ service: registrationService, logger }));
  app.use('/api', exportRouter({ service: exportService, config, logger }));

  app.use(notFoundHandler());
  app.use(errorHandler(logger));

  return {
    app,
    config,
    logger,
    database,
    repository,
    backupStore,
    catalogue,
    mail,
    close(): void {
      database.close();
    },
  };
}
