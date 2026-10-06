import { describe, expect, it } from "vitest";

// AR-01 / AR-07: only src/api talks to the backend, only via relative /api paths; no secrets.
const SOURCES = import.meta.glob(["./**/*.{ts,tsx}", "!./**/*.test.{ts,tsx}"], {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

describe("frontend architecture", () => {
  it("finds the production sources", () => {
    expect(Object.keys(SOURCES)).toContain("./api/client.ts");
  });

  it("calls fetch only from src/api with /api paths (AR-01)", () => {
    for (const [file, text] of Object.entries(SOURCES)) {
      if (file.startsWith("./api/")) {
        for (const m of text.matchAll(/fetch\(\s*"([^"]+)"/g)) {
          expect(m[1]).toMatch(/^\/api\//);
        }
      } else {
        expect(text, file).not.toMatch(/\bfetch\(/);
      }
    }
  });

  it("contains no secret key material (AR-07)", () => {
    for (const [file, text] of Object.entries(SOURCES)) {
      expect(text, file).not.toMatch(/secret|password/i);
    }
  });
});
