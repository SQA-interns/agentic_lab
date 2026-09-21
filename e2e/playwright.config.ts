import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end configuration (specification § 14).
 *
 * The suite starts the real backend and the real frontend and drives a browser against
 * them, so it covers what no in-process test can: the browser actually rendering the
 * configuration, actually posting to the API, and actually showing the confirmation only
 * after a success response.
 */
const DATA_DIR = process.env.E2E_DATA_DIR ?? './.e2e-data';

export default defineConfig({
  testDir: './specs',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 30_000,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:4173',
    trace: 'off',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 900 } } },
    { name: 'mobile', use: { ...devices['Desktop Chrome'], viewport: { width: 375, height: 812 } } },
  ],
  webServer: [
    {
      command: 'node ../backend/dist/server.js',
      port: 3100,
      reuseExistingServer: false,
      stdout: 'ignore',
      env: {
        NODE_ENV: 'development',
        PORT: '3100',
        DATA_DIR,
        OPTIONS_CONFIG_FILE: '../config/conference-options.json',
        FORM_TOKEN_SECRET: 'e2e-form-token-secret-value-long-enough',
        FORM_MIN_FILL_SECONDS: '0',
        CORS_ALLOWED_ORIGINS: 'http://localhost:4173',
        MAIL_TRANSPORT: 'json',
        MAIL_FROM: 'conference@example.org',
        ORGANIZER_EMAILS: 'organizer@example.org',
        EXPORT_USERNAME: 'organizer',
        EXPORT_PASSWORD: 'e2e-export-password',
        LOG_LEVEL: 'silent',
        RATE_LIMIT_REGISTRATION_MAX: '50',
      },
    },
    {
      command: 'npm run preview --workspace frontend -- --port 4173 --strictPort',
      port: 4173,
      reuseExistingServer: false,
      stdout: 'ignore',
      cwd: '..',
      // The preview server stands in for nginx and must proxy /api to the test backend.
      env: { API_PROXY_TARGET: 'http://localhost:3100' },
    },
  ],
});
