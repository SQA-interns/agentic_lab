import { defineConfig, devices } from "@playwright/test";

/** End-to-end tests against the running local stack (docker compose), see verify.sh "e2e". */
export default defineConfig({
  testDir: "e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: true,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: [["list"], ["json", { outputFile: "reports/e2e/results.json" }]],
  outputDir: "test-results",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://127.0.0.1:8088",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
