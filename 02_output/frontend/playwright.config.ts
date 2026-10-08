import { defineConfig, devices } from "@playwright/test";

// End-to-end tests start their own fresh stack and never reuse a running server (KP-08).
export default defineConfig({
  testDir: "./tests/e2e",
  reporter: [["list"], ["html", { open: "never" }]],
  use: { baseURL: "http://127.0.0.1:4173" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
