const { DatabaseSync } = require('node:sqlite');
const fs = require('node:fs');
const path = require('node:path');
const config = require('../../config');

let dbInstance = null;

/**
 * Initializes and returns the SQLite database connection.
 * @param {string} [customPath] 
 * @returns {DatabaseSync}
 */
function initDatabase(customPath) {
  const dbFile = customPath || config.dbPath;

  if (dbFile !== ':memory:') {
    const dir = path.dirname(dbFile);
    if (!fs.existsSync(dir)) {
      fs.mkdirSync(dir, { recursive: true });
    }
  }

  const db = new DatabaseSync(dbFile);

  // Enable WAL mode and foreign keys for durability and performance
  if (dbFile !== ':memory:') {
    db.exec('PRAGMA journal_mode = WAL;');
  }
  db.exec('PRAGMA foreign_keys = ON;');

  // Create registrations table and indexes
  db.exec(`
    CREATE TABLE IF NOT EXISTS registrations (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      uuid TEXT NOT NULL UNIQUE,
      registration_type TEXT NOT NULL CHECK(registration_type IN ('external', 'student')),
      first_name TEXT NOT NULL,
      last_name TEXT NOT NULL,
      email TEXT NOT NULL,
      organization TEXT NULL,
      study_institution TEXT NULL,
      study_programme TEXT NULL,
      student_id TEXT NULL,
      selected_options TEXT NOT NULL,
      privacy_consent INTEGER NOT NULL CHECK(privacy_consent = 1),
      ip_address TEXT NULL,
      user_agent TEXT NULL,
      created_at TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
    );

    CREATE INDEX IF NOT EXISTS idx_registrations_email ON registrations(email);
    CREATE INDEX IF NOT EXISTS idx_registrations_created_at ON registrations(created_at);
    CREATE INDEX IF NOT EXISTS idx_registrations_type ON registrations(registration_type);
  `);

  dbInstance = db;
  return db;
}

/**
 * Returns active database instance or initializes default.
 * @returns {DatabaseSync}
 */
function getDatabase() {
  if (!dbInstance) {
    dbInstance = initDatabase();
  }
  return dbInstance;
}

/**
 * Closes current database connection.
 */
function closeDatabase() {
  if (dbInstance) {
    try {
      dbInstance.close();
    } catch (e) {
      // Ignore if already closed
    }
    dbInstance = null;
  }
}

module.exports = {
  initDatabase,
  getDatabase,
  closeDatabase
};
