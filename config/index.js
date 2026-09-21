const path = require('node:path');

const rootDir = path.resolve(__dirname, '..');

module.exports = {
  port: parseInt(process.env.PORT || '3000', 10),
  host: process.env.HOST || '0.0.0.0',
  nodeEnv: process.env.NODE_ENV || 'development',
  dbPath: process.env.DB_PATH || path.join(rootDir, 'data', 'database.sqlite'),
  backupDir: process.env.BACKUP_DIR || path.join(rootDir, 'data', 'backups'),
  conferenceOptionsPath: process.env.OPTIONS_PATH || path.join(__dirname, 'conference-options.json'),
  organizerEmail: process.env.ORGANIZER_EMAIL || 'organizator@konferenca.si',
  smtp: {
    host: process.env.SMTP_HOST || '',
    port: parseInt(process.env.SMTP_PORT || '587', 10),
    user: process.env.SMTP_USER || '',
    pass: process.env.SMTP_PASS || '',
    from: process.env.SMTP_FROM || 'noreply@konferenca.si'
  },
  rateLimit: {
    windowMs: 5 * 60 * 1000, // 5 minutes
    maxRequests: parseInt(process.env.RATE_LIMIT_MAX || '20', 10)
  }
};
