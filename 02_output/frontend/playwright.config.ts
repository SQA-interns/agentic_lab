import { defineConfig, devices } from "@playwright/test";
import { E2E_FRONTEND_PORT } from "./tests/e2e/stack";

// End-to-end tests start their own fresh stack and never reuse a running server (KP-08).
export default defineConfig({
  testDir: "./tests/e2e",
  globalSetup: "./tests/e2e/global-setup.ts",
  globalTeardown: "./tests/e2e/global-teardown.ts",
  workers: 1,
  retries: 0,
  timeout: 90_000,
  reporter: [["list"], ["html", { open: "never" }]],
  use: { baseURL: `http://127.0.0.1:${E2E_FRONTEND_PORT}`, actionTimeout: 10_000 },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
