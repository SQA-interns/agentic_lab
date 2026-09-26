import { describe, expect, it } from "vitest";
import type { ConsentDefinition, FormValues } from "./types";
import { EMAIL_PATTERN, MESSAGES, validateForm } from "./validation";

const consents: ConsentDefinition[] = [
  { id: "privacy", label: "Privacy", required: true },
  { id: "photos", label: "Photos", required: false },
];

const external: FormValues = {
  firstName: "Žiga",
  lastName: "Šušteršič",
  email: "ziga@example.si",
  organization: "Univerza v Ljubljani",
  studyInstitution: "",
  studyProgramme: "",
  studentId: "",
};

const student: FormValues = {
  ...external,
  organization: "",
  studyInstitution: "FRI",
  studyProgramme: "Informatika",
  studentId: "63200001",
};

describe("validateForm", () => {
  it("accepts a valid external registration", () => {
    expect(
      validateForm("EXTERNAL", external, consents, new Set(["privacy"])),
    ).toEqual({});
  });

  it("accepts a valid student registration without organization", () => {
    expect(
      validateForm("STUDENT", student, consents, new Set(["privacy"])),
    ).toEqual({});
  });

  it("does not require student fields for external participants", () => {
    const errors = validateForm(
      "EXTERNAL",
      external,
      consents,
      new Set(["privacy"]),
    );
    expect(errors).not.toHaveProperty("studentId");
  });

  it("reports required fields, treating whitespace as empty", () => {
    const errors = validateForm(
      "STUDENT",
      { ...student, firstName: "   ", studentId: "" },
      consents,
      new Set(["privacy"]),
    );
    expect(errors).toEqual({
      firstName: MESSAGES.required,
      studentId: MESSAGES.required,
    });
  });

  it("rejects invalid email addresses", () => {
    const errors = validateForm(
      "EXTERNAL",
      { ...external, email: "not-an-email" },
      consents,
      new Set(["privacy"]),
    );
    expect(errors.email).toBe(MESSAGES.invalidEmail);
  });

  it("accepts email with surrounding whitespace", () => {
    const errors = validateForm(
      "EXTERNAL",
      { ...external, email: "  ziga@example.si " },
      consents,
      new Set(["privacy"]),
    );
    expect(errors).toEqual({});
  });

  it("requires mandatory consent only", () => {
    expect(validateForm("EXTERNAL", external, consents, new Set())).toEqual({
      "consents.privacy": MESSAGES.consentRequired,
    });
  });

  it("rejects overlong values and control characters", () => {
    const errors = validateForm(
      "EXTERNAL",
      { ...external, firstName: "x".repeat(101), lastName: "a\u0007b" },
      consents,
      new Set(["privacy"]),
    );
    expect(errors.firstName).toBe(MESSAGES.tooLong(100));
    expect(errors.lastName).toBe(MESSAGES.invalidChars);
  });
});

describe("EMAIL_PATTERN", () => {
  it.each(["a@b.si", "janez.novak+x@sub.example.com"])(
    "accepts %s",
    (email) => {
      expect(EMAIL_PATTERN.test(email)).toBe(true);
    },
  );

  it.each(["a@b", "a b@c.si", "@c.si", "a@@c.si", "a@c..si"])(
    "rejects %s",
    (email) => {
      expect(EMAIL_PATTERN.test(email)).toBe(false);
    },
  );
});
