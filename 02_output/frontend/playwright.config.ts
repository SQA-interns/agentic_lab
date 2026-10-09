import { defineConfig, devices } from "@playwright/test";

// End-to-end tests against the local stack (docker compose up in 02_output); run in the
// Playwright container joined to the stack's network (verify.sh e2e).
export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.e2e.ts",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: process.env["E2E_BASE_URL"] ?? "http://127.0.0.1:8080",
    trace: "off",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
