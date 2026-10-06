import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    // The end-to-end container reaches the host as host.docker.internal (verify.sh "e2e").
    allowedHosts: ["host.docker.internal"],
    proxy: {
      "/api": process.env.BACKEND_URL ?? "http://127.0.0.1:8080",
    },
  },
  preview: {
    allowedHosts: ["host.docker.internal"],
  },
  test: {
    environment: "jsdom",
    setupFiles: ["src/setupTests.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      exclude: ["src/**/*.test.{ts,tsx}", "src/setupTests.ts", "src/main.tsx"],
      reporter: ["text-summary", "json-summary", "html"],
    },
  },
});
