import { defineConfig } from "@playwright/test";

// The e2e acceptance suite expects the backend on :8080 (proxied by Vite under
// /api) in reCAPTCHA test mode — see docs/test-strategy.md for how to start it.
const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:5173";

export default defineConfig({
  testDir: "./e2e",
  workers: 1,
  use: {
    baseURL,
  },
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: "npm run dev",
        url: "http://localhost:5173",
        reuseExistingServer: true,
        timeout: 60_000,
      },
});
