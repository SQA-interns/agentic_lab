import { describe, expect, it } from "vitest";
import type { CategoryOptions, Consent } from "./types";
import { fieldsFor, textFieldError, validateDraft } from "./validation";

const categories: CategoryOptions[] = [
  {
    category: "workshop",
    maxSelections: 1,
    options: [
      { id: "a", name: "A", availableTo: ["EXTERNAL"] },
      { id: "b", name: "B", availableTo: ["EXTERNAL"] },
    ],
  },
];
const consents: Consent[] = [
  { id: "data", text: "Data", mandatory: true },
  { id: "photos", text: "Photos", mandatory: false },
];

describe("text field rules", () => {
  it("requires a value after trimming", () => {
    expect(textFieldError("firstName", "  \t ")).toBe("First name is required.");
    expect(textFieldError("firstName", " Ana ")).toBeNull();
  });

  it("rejects control characters inside the value", () => {
    expect(textFieldError("lastName", "No\nvak")).toBe(
      "Last name must not contain line breaks or other control characters.",
    );
    expect(textFieldError("lastName", "Novak\u0007")).not.toBeNull();
  });

  it("counts code points against the maximum", () => {
    expect(textFieldError("studentId", "😀".repeat(50))).toBeNull();
    expect(textFieldError("studentId", "😀".repeat(51))).toBe(
      "Student ID must be at most 50 characters.",
    );
    expect(textFieldError("organization", "x".repeat(200))).toBeNull();
    expect(textFieldError("organization", "x".repeat(201))).not.toBeNull();
  });

  it("checks the email format", () => {
    for (const bad of ["a@b", "a b@c.si", "a@@c.si", "@c.si", "a@c."]) {
      expect(textFieldError("email", bad)).toBe("Enter a valid email address.");
    }
    expect(textFieldError("email", "ana@example.si")).toBeNull();
    expect(textFieldError("email", `${"x".repeat(244)}@example.si`)).not.toBeNull();
  });

  it("accepts Slovenian characters", () => {
    expect(textFieldError("firstName", "Črtomir Šuštar Žižek")).toBeNull();
  });

  it("lists the fields of each type", () => {
    expect(fieldsFor("EXTERNAL")).toEqual(["firstName", "lastName", "email", "organization"]);
    expect(fieldsFor("STUDENT")).toHaveLength(6);
  });
});

describe("draft validation", () => {
  const valid = {
    type: "EXTERNAL" as const,
    values: { firstName: "Ana", lastName: "Novak", email: "ana@example.si", organization: "IJS" },
    optionIds: ["a"],
    consentIds: ["data"],
    captchaToken: "t",
  };

  it("accepts a complete draft", () => {
    expect(validateDraft(valid, categories, consents)).toEqual({});
  });

  it("reports too many options, a missing mandatory consent and a missing token", () => {
    const errors = validateDraft(
      { ...valid, optionIds: ["a", "b"], consentIds: ["photos"], captchaToken: "" },
      categories,
      consents,
    );

    expect(errors).toEqual({
      optionIds: "Select at most 1 of workshops.",
      consentIds: "Please give the required consent.",
      captchaToken: "Please confirm that you are not a robot.",
    });
  });

  it("reports missing fields of the chosen type only", () => {
    const errors = validateDraft(
      { ...valid, type: "STUDENT", values: { firstName: "Ana" } },
      categories,
      consents,
    );

    expect(Object.keys(errors).sort()).toEqual([
      "email",
      "lastName",
      "studentId",
      "studyInstitution",
      "studyProgramme",
    ]);
  });
});
