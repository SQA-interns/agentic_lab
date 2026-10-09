import { describe, expect, it } from "vitest";
import { externalForm } from "./test/fixtures";
import { trimValue, validate, type Draft } from "./validation";

const valid: Draft = {
  values: {
    firstName: "Ana",
    lastName: "Novak",
    email: "ana@example.si",
    organization: "IJS",
  },
  optionIds: ["ws-a"],
  consentIds: ["privacy"],
  recaptchaToken: "test-pass",
};

describe("validation (NFR-03, BR-02, BR-03)", () => {
  it("KP-03 trims Unicode whitespace including the no-break space", () => {
    expect(trimValue("  Ana  ﻿")).toBe("Ana");
    expect(trimValue("  ")).toBe("");
  });

  it("AC-001-02 accepts a valid draft", () => {
    expect(validate(externalForm, valid)).toEqual([]);
  });

  it("AC-001-04 AC-001-05 reports each empty or whitespace-only field", () => {
    const errors = validate(externalForm, {
      ...valid,
      values: { ...valid.values, firstName: " ", organization: "" },
    });
    expect(errors).toEqual([
      { field: "firstName", code: "REQUIRED" },
      { field: "organization", code: "REQUIRED" },
    ]);
  });

  it("AC-001-07 reports an invalid email", () => {
    for (const email of ["ana", "ana@si", "a b@x.si", "ana@x.si b"]) {
      expect(
        validate(externalForm, {
          ...valid,
          values: { ...valid.values, email },
        }),
      ).toEqual([{ field: "email", code: "INVALID_EMAIL" }]);
    }
  });

  it("reports a value above the maximum length", () => {
    const errors = validate(externalForm, {
      ...valid,
      values: { ...valid.values, lastName: "x".repeat(101) },
    });
    expect(errors).toEqual([{ field: "lastName", code: "TOO_LONG" }]);
  });

  it("AC-001-12 reports too many options in one category", () => {
    expect(
      validate(externalForm, { ...valid, optionIds: ["ws-a", "ws-b"] }),
    ).toEqual([{ field: "optionIds", code: "TOO_MANY_OPTIONS" }]);
  });

  it("AC-001-14 names each missing mandatory consent only", () => {
    expect(validate(externalForm, { ...valid, consentIds: ["photo"] })).toEqual(
      [{ field: "consentIds", code: "CONSENT_REQUIRED", consentId: "privacy" }],
    );
  });

  it("AC-001-16 requires the anti-automation token", () => {
    expect(validate(externalForm, { ...valid, recaptchaToken: "" })).toEqual([
      { field: "recaptchaToken", code: "CAPTCHA_FAILED" },
    ]);
  });
});
