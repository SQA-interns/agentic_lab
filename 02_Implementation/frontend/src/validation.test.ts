import { describe, expect, it } from "vitest";
import { FIELDS } from "./fields";
import { validate } from "./validation";

const consents = [{ id: "privacy", text: "Consent", mandatory: true }];

const validExternal = {
  firstName: "Ana",
  lastName: "Novak",
  email: "ana@example.si",
  organization: "IJS",
};

describe("validate", () => {
  it("accepts a complete external registration", () => {
    expect(
      validate(FIELDS.EXTERNAL, validExternal, consents, { privacy: true }),
    ).toEqual({});
  });

  it("requires every fixed field, treating whitespace as empty", () => {
    const errors = validate(
      FIELDS.STUDENT,
      { firstName: "  ", email: "a@b.si" },
      consents,
      { privacy: true },
    );
    expect(Object.keys(errors).sort()).toEqual(
      [
        "firstName",
        "lastName",
        "studentId",
        "studyInstitution",
        "studyProgramme",
      ].sort(),
    );
    expect(errors.firstName).toBe("This field is required.");
  });

  it.each(["ana", "ana@", "@x.si", "ana@x", "a b@x.si"])(
    "rejects invalid email %s",
    (email) => {
      const errors = validate(
        FIELDS.EXTERNAL,
        { ...validExternal, email },
        consents,
        { privacy: true },
      );
      expect(errors.email).toBe("Email must be a valid email address.");
    },
  );

  it("accepts Slovenian characters and trims surrounding whitespace", () => {
    expect(
      validate(
        FIELDS.EXTERNAL,
        { ...validExternal, firstName: "  Žiga Čeh Šolar  " },
        consents,
        { privacy: true },
      ),
    ).toEqual({});
  });

  it("rejects too long values and control characters", () => {
    const errors = validate(
      FIELDS.EXTERNAL,
      { ...validExternal, firstName: "a".repeat(101), lastName: "No\u0007vak" },
      consents,
      { privacy: true },
    );
    expect(errors.firstName).toBe("This value is too long.");
    expect(errors.lastName).toBe("This value contains invalid characters.");
  });

  it("requires mandatory consents", () => {
    const errors = validate(FIELDS.EXTERNAL, validExternal, consents, {});
    expect(errors["consents.privacy"]).toBe("This consent is required.");
  });

  it("does not require optional consents", () => {
    expect(
      validate(
        FIELDS.EXTERNAL,
        validExternal,
        [{ id: "news", text: "News", mandatory: false }],
        {},
      ),
    ).toEqual({});
  });
});
