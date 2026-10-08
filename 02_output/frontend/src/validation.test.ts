import { describe, expect, it } from "vitest";
import type { FormConfig } from "./api";
import { clean, isValidEmail, MESSAGES, validate } from "./validation";

const config: FormConfig = {
  conferenceName: "C",
  recaptcha: { siteKey: "", testMode: true },
  categories: [
    { category: "WORKSHOP", maxSelections: 1 },
    { category: "EVENT", maxSelections: 2 },
  ],
  options: [
    { id: "a", name: "A", category: "WORKSHOP", registrationTypes: ["EXTERNAL", "STUDENT"] },
    { id: "b", name: "B", category: "WORKSHOP", registrationTypes: ["EXTERNAL", "STUDENT"] },
  ],
  consents: [
    { id: "data", text: "D", mandatory: true },
    { id: "news", text: "N", mandatory: false },
  ],
};

const external = {
  firstName: "Ana",
  lastName: "Novak",
  email: "ana@example.si",
  organization: "O",
};

describe("clean (KP-03)", () => {
  it("removes NBSP, em space and BOM at the ends only", () => {
    expect(clean("  ﻿ Ana  Novak\t ")).toBe("Ana  Novak");
    expect(clean(undefined)).toBe("");
  });
});

describe("isValidEmail (BR-03)", () => {
  it.each(["ana@example.si", "žan@šola.si", "a+b@sub.example.com", "x@xn--d1acufc.si"])(
    "accepts %s",
    (email) => expect(isValidEmail(email)).toBe(true),
  );
  it.each([
    "plain",
    "ana@",
    "@example.si",
    "ana@example",
    "ana@@example.si",
    "ana novak@example.si",
    "ana@example..si",
    "ana@-x.si",
    "ana@example.s",
    "ana@example.12",
    "a\r\n@example.si",
    `${"a".repeat(65)}@example.si`,
  ])("rejects %s", (email) => expect(isValidEmail(email)).toBe(false));
});

describe("validate (NFR-03)", () => {
  it("passes a complete external registration", () => {
    expect(validate("EXTERNAL", external, ["a"], ["data"], "t", config)).toEqual({});
  });

  it("requires the fields of the chosen type only", () => {
    const errors = validate("STUDENT", external, [], ["data"], "t", config);
    expect(Object.keys(errors).sort()).toEqual(["studentId", "studyInstitution", "studyProgramme"]);
    expect(errors.studentId).toBe(MESSAGES.required);
  });

  it("reports too long values in code points and control characters", () => {
    const errors = validate(
      "EXTERNAL",
      { ...external, firstName: "č".repeat(101), lastName: "No\nvak" },
      [],
      ["data"],
      "t",
      config,
    );
    expect(errors.firstName).toBe(MESSAGES.too_long);
    expect(errors.lastName).toBe(MESSAGES.invalid_characters);
    expect(
      validate("EXTERNAL", { ...external, firstName: "č".repeat(100) }, [], ["data"], "t", config),
    ).toEqual({});
  });

  it("limits options per category (D-14)", () => {
    expect(validate("EXTERNAL", external, ["a", "b"], ["data"], "t", config).optionIds).toBe(
      MESSAGES.too_many_options,
    );
  });

  it("requires mandatory consents only (BR-05)", () => {
    expect(validate("EXTERNAL", external, [], ["news"], "t", config).consentIds).toBe(
      MESSAGES.consent_missing,
    );
    expect(validate("EXTERNAL", external, [], ["data"], "t", config).consentIds).toBeUndefined();
  });

  it("requires the anti-automation token (SR-01)", () => {
    expect(validate("EXTERNAL", external, [], ["data"], "", config).recaptchaToken).toBe(
      MESSAGES.captcha_failed,
    );
  });
});
