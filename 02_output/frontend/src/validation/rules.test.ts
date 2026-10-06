import { describe, expect, it } from "vitest";
import { FIELDS_BY_TYPE, checkText, strip } from "./rules";

describe("strip", () => {
  it.each(["\u00A0", "\u2007", "\u202F", "\u3000", "\uFEFF", "\u0085", "\t", "\n", "\u2028", " "])(
    "removes %j at both edges",
    (ws) => {
      expect(strip(`${ws}Ana${ws}${ws}`)).toBe("Ana");
    },
  );

  it("keeps inner whitespace", () => {
    expect(strip(" Ana Marija ")).toBe("Ana Marija");
  });
});

describe("checkText", () => {
  it("reports required, characters, length and email in that order", () => {
    expect(checkText("firstName", "\u00A0")).toBe("REQUIRED");
    expect(checkText("lastName", "No\u0000vak")).toBe("INVALID_CHARACTERS");
    expect(checkText("lastName", "a\u200Bb")).toBe("INVALID_CHARACTERS");
    expect(checkText("firstName", "a".repeat(101))).toBe("TOO_LONG");
    expect(checkText("firstName", "a".repeat(100))).toBeUndefined();
    expect(checkText("studentId", "1".repeat(51))).toBe("TOO_LONG");
    expect(checkText("email", "a@b")).toBe("INVALID_EMAIL");
    expect(checkText("email", "a\u00A0b@c.si")).toBe("INVALID_EMAIL");
    expect(checkText("email", " ana@example.si ")).toBeUndefined();
    expect(checkText("email", "ana@example.si x")).toBe("INVALID_EMAIL");
    expect(checkText("email", "ana@example.si@x")).toBe("INVALID_EMAIL");
  });

  it("counts code points, not UTF-16 units", () => {
    expect(checkText("firstName", "\u{1F600}".repeat(100))).toBeUndefined();
  });

  it("accepts Slovenian letters", () => {
    expect(checkText("organization", "Občina Škofja Loka – Žiri")).toBeUndefined();
  });
});

describe("FIELDS_BY_TYPE", () => {
  it("lists the fixed fields of each form (BR-01)", () => {
    expect(FIELDS_BY_TYPE.EXTERNAL).toEqual(["firstName", "lastName", "email", "organization"]);
    expect(FIELDS_BY_TYPE.STUDENT).toHaveLength(6);
  });
});
