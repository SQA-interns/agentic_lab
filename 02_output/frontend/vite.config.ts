/// <reference types="vitest/config" />
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    proxy: {
      // The local stack's nginx; the Host header stays the dev server's, so requests are
      // same-origin for the backend (no CORS setting needed).
      "/api": { target: "http://127.0.0.1:8080", changeOrigin: false },
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    passWithNoTests: true,
    coverage: {
      provider: "v8",
      include: ["src/**/*.{ts,tsx}"],
      exclude: [
        "src/**/*.test.{ts,tsx}",
        "src/test/**",
        "src/acceptance/**",
        "src/main.tsx",
        "src/vite-env.d.ts",
      ],
      reporter: ["text-summary", "json-summary", "html"],
    },
  },
});
