import { describe, expect, it } from "vitest";
import type { FormConfig } from "./api";
import { categoriesOverLimit, checkText, isValidEmail } from "./validation";

describe("validation", () => {
  it.each(["a@b.si", "ana.horvat@uni-lj.si", "š@ž.si", "x+y@sub.example.com"])(
    "accepts the email %s",
    (email) => expect(isValidEmail(email)).toBe(true),
  );

  it.each(["a@b", "a@.b.si", "a@b.si.", "a..b@c.si", "@b.si", "a b@c.si", "a@@b.si", "a@b.si x"])(
    "rejects the email %s",
    (email) => expect(isValidEmail(email)).toBe(false),
  );

  it("reports required, control characters, length and format in that order", () => {
    expect(checkText("   ", 10, false)).toBe("REQUIRED");
    expect(checkText("a\u0007b", 10, false)).toBe("CONTROL_CHARACTER");
    expect(checkText("a\nb", 10, true)).toBe("CONTROL_CHARACTER");
    expect(checkText("abcdefghijk", 10, false)).toBe("TOO_LONG");
    expect(checkText("abcdefghij", 10, false)).toBeNull();
    expect(checkText(" no-at-sign ", 50, true)).toBe("INVALID_FORMAT");
    expect(checkText("  a@b.si  ", 6, true)).toBeNull();
  });

  it("finds categories above their maximum only", () => {
    const config = {
      categories: [{ id: "WORKSHOP", maxSelections: 1 }, { id: "MEAL" }],
      options: [
        { id: "w1", category: "WORKSHOP" },
        { id: "w2", category: "WORKSHOP" },
        { id: "m1", category: "MEAL" },
        { id: "m2", category: "MEAL" },
      ],
    } as unknown as FormConfig;

    expect(categoriesOverLimit(config, new Set(["w1", "m1", "m2"]))).toEqual([]);
    expect(categoriesOverLimit(config, new Set(["w1", "w2"]))).toEqual(["WORKSHOP"]);
  });
});
