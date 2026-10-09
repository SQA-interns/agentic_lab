import { defineConfig, devices } from "@playwright/test";

// End-to-end tests start their own fresh stack on free ports (KP-08); see e2e/support/stack.ts.
export default defineConfig({
  testDir: "./e2e",
  testMatch: "**/*.e2e.spec.ts",
  globalSetup: "./e2e/support/global-setup.ts",
  globalTeardown: "./e2e/support/global-teardown.ts",
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 30_000,
  expect: { timeout: 5_000 },
  reporter: [["list"], ["junit", { outputFile: "reports/e2e-junit.xml" }]],
  use: { ...devices["Desktop Chrome"], trace: "retain-on-failure" },
});
