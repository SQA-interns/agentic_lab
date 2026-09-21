const config = require('../config');
const { initDatabase, closeDatabase } = require('./db/database');
const createApp = require('./app');

// Initialize database
initDatabase();

const app = createApp();

const server = app.listen(config.port, config.host, () => {
  console.log(`[Server] Conference Registration System running on http://${config.host}:${config.port}`);
});

function gracefulShutdown(signal) {
  console.log(`[Server] Received ${signal}. Shutting down gracefully...`);
  server.close(() => {
    closeDatabase();
    console.log('[Server] Closed HTTP server and database.');
    process.exit(0);
  });

  // Force exit after 10s if dangling
  setTimeout(() => {
    console.error('[Server] Forced shutdown after timeout.');
    process.exit(1);
  }, 10000).unref();
}

process.on('SIGTERM', () => gracefulShutdown('SIGTERM'));
process.on('SIGINT', () => gracefulShutdown('SIGINT'));

module.exports = server;
