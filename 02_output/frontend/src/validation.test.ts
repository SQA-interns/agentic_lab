import { describe, expect, it } from "vitest";
import { MESSAGES, isValidEmail, validate } from "./validation";

const consents = [
  { id: "data", text: "Data", mandatory: true },
  { id: "photo", text: "Photo", mandatory: false },
];

const external = {
  firstName: "Ana",
  lastName: "Novak",
  email: "ana@example.com",
  organization: "IJS",
};

describe("validate", () => {
  it("accepts a complete external registration", () => {
    expect(
      validate("external", external, consents, new Set(["data"]), "t"),
    ).toEqual({});
  });

  it("requires every field of the chosen type only", () => {
    const errors = validate(
      "student",
      external,
      consents,
      new Set(["data"]),
      "t",
    );
    expect(errors).toEqual({
      studyInstitution: MESSAGES.required,
      studyProgramme: MESSAGES.required,
      studentId: MESSAGES.required,
    });
  });

  it("treats whitespace as empty and checks the email format after trimming", () => {
    const errors = validate(
      "external",
      { ...external, firstName: " \t ", email: "  ana@example.com  " },
      consents,
      new Set(["data"]),
      "t",
    );
    expect(errors).toEqual({ firstName: MESSAGES.required });
    expect(
      validate(
        "external",
        { ...external, email: "ana@example" },
        consents,
        new Set(["data"]),
        "t",
      ),
    ).toEqual({ email: MESSAGES.email });
  });

  it("requires mandatory consents and the captcha token", () => {
    expect(
      validate("external", external, consents, new Set(["photo"]), ""),
    ).toEqual({
      "consents.data": MESSAGES.consent,
      captchaToken: MESSAGES.captcha,
    });
  });

  it("reports an empty email as required, not as invalid", () => {
    expect(
      validate(
        "external",
        { ...external, email: "" },
        consents,
        new Set(["data"]),
        "t",
      ),
    ).toEqual({
      email: MESSAGES.required,
    });
  });
});

describe("isValidEmail", () => {
  it.each([
    ["a@b.si", true],
    [" a@b.si ", true],
    ["a@b", false],
    ["a b@c.si", false],
    ["a@@b.si", false],
    ["@b.si", false],
  ])("%s -> %s", (value, valid) => {
    expect(isValidEmail(value)).toBe(valid);
  });
});
