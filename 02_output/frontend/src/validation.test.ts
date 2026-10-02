// Unit tests of the client-side checks (NFR-03).
import { describe, expect, it } from "vitest";
import { FORM_FIELDS } from "./texts";
import { type FormValues, validate } from "./validation";

const VALID_EXTERNAL: FormValues = {
  texts: {
    firstName: "Živa",
    lastName: "Čučnik",
    email: "ziva@example.org",
    organization: "Inštitut",
  },
  consent: true,
  captchaToken: "token",
};

function withText(name: string, value: string): FormValues {
  return { ...VALID_EXTERNAL, texts: { ...VALID_EXTERNAL.texts, [name]: value } };
}

describe("validate", () => {
  it("accepts a complete external and a complete student form", () => {
    expect(validate(FORM_FIELDS.EXTERNAL, VALID_EXTERNAL)).toEqual({});
    expect(
      validate(FORM_FIELDS.STUDENT, {
        texts: {
          firstName: "Žan",
          lastName: "Košir",
          email: "zan@example.org",
          studyInstitution: "UL",
          studyProgramme: "RI",
          studentId: "63210001",
        },
        consent: true,
        captchaToken: "token",
      }),
    ).toEqual({});
  });

  it("NFR-03 reports every missing field of the chosen form, consent and anti-automation check", () => {
    expect(validate(FORM_FIELDS.STUDENT, { texts: {}, consent: false, captchaToken: "" })).toEqual({
      firstName: "required",
      lastName: "required",
      email: "required",
      studyInstitution: "required",
      studyProgramme: "required",
      studentId: "required",
      consent: "consent_required",
      captchaToken: "captcha_failed",
    });
  });

  it("does not ask an external participant for student fields", () => {
    expect(Object.keys(validate(FORM_FIELDS.EXTERNAL, { ...VALID_EXTERNAL, texts: {} }))).toEqual([
      "firstName",
      "lastName",
      "email",
      "organization",
    ]);
  });

  it("treats a value of whitespace only as missing", () => {
    expect(validate(FORM_FIELDS.EXTERNAL, withText("organization", " \t  "))).toEqual({
      organization: "required",
    });
  });

  it("accepts a text at its limit and reports one character more as too long", () => {
    expect(validate(FORM_FIELDS.EXTERNAL, withText("firstName", "č".repeat(100)))).toEqual({});
    expect(validate(FORM_FIELDS.EXTERNAL, withText("firstName", "č".repeat(101)))).toEqual({
      firstName: "too_long",
    });
    // Characters outside the basic plane count once, as in the backend.
    expect(validate(FORM_FIELDS.EXTERNAL, withText("firstName", "😀".repeat(100)))).toEqual({});
  });

  it.each([
    "ziva",
    "ziva@",
    "@example.org",
    "ziva@example",
    "ziva @example.org",
    "ziva@@example.org",
    "ziva@example..org",
    "ziva@example.org.",
  ])("reports the email %s as invalid", (email) => {
    expect(validate(FORM_FIELDS.EXTERNAL, withText("email", email))).toEqual({
      email: "invalid_format",
    });
  });

  it.each(["a@b.si", "ime.priimek+oznaka@pod.domena.example.org", "  ziva@example.org  "])(
    "accepts the email %s",
    (email) => {
      expect(validate(FORM_FIELDS.EXTERNAL, withText("email", email))).toEqual({});
    },
  );
});
