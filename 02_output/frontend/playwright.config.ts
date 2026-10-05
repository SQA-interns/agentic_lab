import { defineConfig, devices } from "@playwright/test";

// Runs inside the Playwright container (02_output/scripts/verify.sh e2e) against the running local stack.
export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [["list"], ["json", { outputFile: "reports/e2e.json" }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://frontend:8080",
    trace: "off",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
