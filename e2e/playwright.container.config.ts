import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end configuration for the **containerized** deployment.
 *
 * Same specs as `playwright.config.ts`, but pointed at the running compose stack instead
 * of starting its own servers. This is what verifies AC-G-14: the acceptance-level
 * behaviour is checked against the artefact that actually ships — the built images, nginx
 * with its real security headers, and the backend behind the same-origin `/api` proxy —
 * rather than against processes started by the test runner.
 *
 * Prerequisite: `docker compose up -d` with a `.env` whose registration rate limit is
 * high enough for a full suite run (the shipped default of 5 per 10 minutes is a
 * production value and is covered separately by the API tests).
 */
export default defineConfig({
  // Both the shared acceptance specs and the deployment-only specs.
  testDir: '.',
  testMatch: ['specs/**/*.spec.ts', 'container-specs/**/*.spec.ts'],
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 30_000,
  reporter: [['list']],
  use: {
    baseURL: process.env.CONTAINER_BASE_URL ?? 'http://localhost:8080',
    trace: 'off',
  },
  projects: [
    { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 900 } } },
    { name: 'mobile', use: { ...devices['Desktop Chrome'], viewport: { width: 375, height: 812 } } },
  ],
});
