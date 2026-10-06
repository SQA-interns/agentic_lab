import type { FormConfig, RegistrationType } from "./api";

/** Client copies of the backend field rules (docs/02_specification.md section 3 step 3, NFR-03). */

export type TextField =
  | "firstName"
  | "lastName"
  | "email"
  | "organization"
  | "studyInstitution"
  | "studyProgramme"
  | "studentId";

export const FIELDS: Record<RegistrationType, { name: TextField; maxLength: number }[]> = {
  EXTERNAL: [
    { name: "firstName", maxLength: 100 },
    { name: "lastName", maxLength: 100 },
    { name: "email", maxLength: 254 },
    { name: "organization", maxLength: 200 },
  ],
  STUDENT: [
    { name: "firstName", maxLength: 100 },
    { name: "lastName", maxLength: 100 },
    { name: "email", maxLength: 254 },
    { name: "studyInstitution", maxLength: 200 },
    { name: "studyProgramme", maxLength: 200 },
    { name: "studentId", maxLength: 50 },
  ],
};

export type FieldCode =
  "REQUIRED" | "INVALID_FORMAT" | "TOO_LONG" | "CONTROL_CHARACTER" | "CATEGORY_LIMIT";

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
// eslint-disable-next-line no-control-regex
const CONTROL = /[\u0000-\u001f\u007f-\u009f]/;

export function isValidEmail(email: string): boolean {
  if (!EMAIL.test(email) || email.includes("..")) {
    return false;
  }
  const domain = email.slice(email.indexOf("@") + 1);
  return !domain.startsWith(".") && !domain.endsWith(".");
}

export function checkText(value: string, maxLength: number, email: boolean): FieldCode | null {
  const trimmed = value.trim();
  if (trimmed === "") {
    return "REQUIRED";
  }
  if (CONTROL.test(trimmed)) {
    return "CONTROL_CHARACTER";
  }
  if (trimmed.length > maxLength) {
    return "TOO_LONG";
  }
  if (email && !isValidEmail(trimmed)) {
    return "INVALID_FORMAT";
  }
  return null;
}

/** Categories whose selected options exceed the configured maximum (D-14). */
export function categoriesOverLimit(config: FormConfig, selected: Set<string>): string[] {
  return config.categories
    .filter((category) => {
      const max = category.maxSelections;
      if (max === undefined) {
        return false;
      }
      const count = config.options.filter(
        (o) => o.category === category.id && selected.has(o.id),
      ).length;
      return count > max;
    })
    .map((category) => category.id);
}
