import { EMPTY_VALUES, FIELDS } from "./fields";
import { MESSAGES, isValidEmail, toRequestBody, validate } from "./validation";

const consents = [
  { id: "privacy", text: "Privacy", required: true },
  { id: "news", text: "News", required: false },
];

describe("validate", () => {
  it("requires every field of the external form and the required consent", () => {
    const errors = validate("EXTERNAL", EMPTY_VALUES, consents, []);

    expect(errors).toEqual({
      firstName: MESSAGES.required,
      lastName: MESSAGES.required,
      email: MESSAGES.required,
      organization: MESSAGES.required,
      consents: MESSAGES.consent,
    });
  });

  it("requires the student fields and ignores organization", () => {
    const errors = validate("STUDENT", { ...EMPTY_VALUES, organization: "x" }, consents, [
      "privacy",
    ]);

    expect(Object.keys(errors)).toEqual([
      "firstName",
      "lastName",
      "email",
      "studyInstitution",
      "studyProgramme",
      "studentId",
    ]);
  });

  it("treats whitespace as empty and checks the email format", () => {
    const values = {
      ...EMPTY_VALUES,
      firstName: "  ",
      lastName: "Novak",
      email: "not-an-email",
      organization: "IJS",
    };

    expect(validate("EXTERNAL", values, consents, ["privacy"])).toEqual({
      firstName: MESSAGES.required,
      email: MESSAGES.email,
    });
  });

  it("accepts a complete external form", () => {
    const values = {
      ...EMPTY_VALUES,
      firstName: "Ana",
      lastName: "Novak",
      email: " ana@example.si ",
      organization: "IJS",
    };

    expect(validate("EXTERNAL", values, consents, ["privacy"])).toEqual({});
  });
});

describe("isValidEmail", () => {
  it.each(["a@b.si", "č@š.si", " a@b.si "])("accepts %s", (email) => {
    expect(isValidEmail(email)).toBe(true);
  });

  it.each(["", "a", "a@b", "a b@c.si", "@c.si", "a@b."])("rejects %s", (email) => {
    expect(isValidEmail(email)).toBe(false);
  });
});

describe("toRequestBody", () => {
  it("sends only the fields of the selected type, trimmed", () => {
    const values = {
      ...EMPTY_VALUES,
      firstName: " Luka ",
      lastName: "Kovač",
      email: "luka@x.si",
      organization: "hidden",
      studyInstitution: "UL",
      studyProgramme: "RI",
      studentId: "6321",
    };

    expect(toRequestBody("STUDENT", values, ["ws"], ["privacy"], "tok")).toEqual({
      type: "STUDENT",
      firstName: "Luka",
      lastName: "Kovač",
      email: "luka@x.si",
      studyInstitution: "UL",
      studyProgramme: "RI",
      studentId: "6321",
      optionIds: ["ws"],
      consents: ["privacy"],
      recaptchaToken: "tok",
    });
  });

  it("defines four external and six student fields", () => {
    expect(FIELDS.EXTERNAL.map((f) => f.label)).toEqual([
      "First name",
      "Last name",
      "Email",
      "Organization / institution",
    ]);
    expect(FIELDS.STUDENT).toHaveLength(6);
  });
});
