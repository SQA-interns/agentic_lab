import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// D-14: unmount each test's page before the next test (Vitest runs without globals).
afterEach(() => {
  cleanup();
});
