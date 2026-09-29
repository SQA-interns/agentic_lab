import { describe, expect, it } from "vitest";
import {
  fieldsFor,
  isValidEmail,
  messageForCode,
  MESSAGES,
  validate,
  type FormValues,
} from "./validate";

const valid: FormValues = {
  type: "EXTERNAL",
  firstName: "Živa",
  lastName: "Čepič",
  email: "ziva@example.si",
  organization: "IJS",
  studyInstitution: "",
  studyProgramme: "",
  studentId: "",
  personalDataConsent: true,
  recaptchaToken: "test-pass",
};

describe("validate", () => {
  it("accepts a complete external registration", () => {
    expect(validate(valid)).toEqual({});
  });

  it("treats whitespace-only values as missing", () => {
    expect(validate({ ...valid, firstName: "   " })).toEqual({
      firstName: MESSAGES.REQUIRED,
    });
  });

  it("validates only the fields of the selected type", () => {
    const errors = validate({ ...valid, type: "STUDENT" });
    expect(errors).toEqual({
      studyInstitution: MESSAGES.REQUIRED,
      studyProgramme: MESSAGES.REQUIRED,
      studentId: MESSAGES.REQUIRED,
    });
  });

  it("requires consent and the anti-robot check", () => {
    expect(
      validate({ ...valid, personalDataConsent: false, recaptchaToken: "" }),
    ).toEqual({
      personalDataConsent: MESSAGES.CONSENT_REQUIRED,
      recaptchaToken: MESSAGES.RECAPTCHA_FAILED,
    });
  });

  it("counts length in code points", () => {
    expect(validate({ ...valid, firstName: "😀".repeat(100) })).toEqual({});
    expect(validate({ ...valid, firstName: "😀".repeat(101) })).toEqual({
      firstName: MESSAGES.TOO_LONG,
    });
  });

  it("rejects control and format characters", () => {
    expect(validate({ ...valid, lastName: "No\u0000vak" }).lastName).toBe(
      MESSAGES.INVALID_CHARACTERS,
    );
    expect(validate({ ...valid, lastName: "No\u200bvak" }).lastName).toBe(
      MESSAGES.INVALID_CHARACTERS,
    );
  });

  it("reports an invalid email", () => {
    expect(validate({ ...valid, email: "ana.novak" }).email).toBe(
      MESSAGES.INVALID_EMAIL,
    );
  });
});

describe("isValidEmail", () => {
  it.each(["a@b.si", "ana.novak+x@fri.uni-lj.si", "živa@primer.si"])(
    "accepts %s",
    (e) => {
      expect(isValidEmail(e)).toBe(true);
    },
  );

  it.each([
    "a@b",
    "a@.b.si",
    "a@b.si.",
    "a@b..si",
    "a b@c.si",
    "a@b@c.si",
    "@b.si",
  ])("rejects %s", (e) => {
    expect(isValidEmail(e)).toBe(false);
  });
});

describe("fieldsFor / messageForCode", () => {
  it("lists type-specific fields", () => {
    expect(fieldsFor("EXTERNAL")).toContain("organization");
    expect(fieldsFor("STUDENT")).not.toContain("organization");
  });

  it("maps known codes and falls back to the server message", () => {
    expect(messageForCode("REQUIRED", "x")).toBe(MESSAGES.REQUIRED);
    expect(messageForCode("FIELD_NOT_ALLOWED", "Server says no.")).toBe(
      "Server says no.",
    );
  });
});
