/**
 * Process entry point (specification § 10, § 13).
 *
 * Fails fast and loudly on a configuration problem, then serves until a termination
 * signal arrives, at which point it drains in-flight requests before closing the
 * database.
 */
import { createApplication } from './api/app.js';
import { loadConfig } from './config/env.js';
import { ConfigurationError } from './domain/errors.js';
import { createLogger } from './infrastructure/logging/logger.js';

function fail(message: string): never {
  process.stderr.write(`${message}\n`);
  process.exit(1);
}

function main(): void {
  let application;
  let config;
  try {
    config = loadConfig();
    application = createApplication(config);
  } catch (error) {
    if (error instanceof ConfigurationError) {
      // A misconfigured deployment must not start and serve broken registrations.
      fail(`Startup aborted. ${error.message}`);
    }
    fail(`Startup aborted. ${error instanceof Error ? error.message : String(error)}`);
  }

  const { app, logger } = application;
  const server = app.listen(config.port, () => {
    logger.info(
      { port: config.port, nodeEnv: config.nodeEnv, dataDir: config.dataDir },
      'conference registration backend started',
    );
  });

  const shutdown = (signal: string): void => {
    logger.info({ signal }, 'shutting down');
    server.close(() => {
      application.close();
      process.exit(0);
    });
    // Do not wait forever for a stuck connection.
    setTimeout(() => process.exit(1), 10_000).unref();
  };

  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));

  process.on('unhandledRejection', (reason) => {
    logger.fatal({ err: reason }, 'unhandled rejection');
    process.exit(1);
  });
  process.on('uncaughtException', (error) => {
    logger.fatal({ err: error }, 'uncaught exception');
    process.exit(1);
  });
}

try {
  main();
} catch (error) {
  createLogger('error', false).fatal({ err: error }, 'fatal startup error');
  process.exit(1);
}
