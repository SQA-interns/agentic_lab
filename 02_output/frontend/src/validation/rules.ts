import type { RegistrationType } from "../api/types";

// Client-side mirror of the server rules (spec section 4, NFR-03, KP-03).

const WHITESPACE = "[\\t\\n\\v\\f\\r\\u001C-\\u001F\\u0085\\uFEFF\\p{Zs}\\u2028\\u2029]";
const EDGES = new RegExp(`^${WHITESPACE}+|${WHITESPACE}+$`, "gu");
const FORBIDDEN = /[\p{Cc}\p{Cf}\u{2028}\u{2029}]/u;
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/u;

export type TextField =
  | "firstName"
  | "lastName"
  | "email"
  | "organization"
  | "studyInstitution"
  | "studyProgramme"
  | "studentId";

export const MAX_LENGTH: Record<TextField, number> = {
  firstName: 100,
  lastName: 100,
  email: 254,
  organization: 200,
  studyInstitution: 200,
  studyProgramme: 200,
  studentId: 50,
};

export const FIELDS_BY_TYPE: Record<RegistrationType, TextField[]> = {
  EXTERNAL: ["firstName", "lastName", "email", "organization"],
  STUDENT: ["firstName", "lastName", "email", "studyInstitution", "studyProgramme", "studentId"],
};

/** Removes leading and trailing whitespace, including no-break spaces. */
export function strip(value: string): string {
  return value.replace(EDGES, "");
}

/** Error code for one text field, or undefined if the value is valid. */
export function checkText(field: TextField, value: string): string | undefined {
  const v = strip(value);
  if (v === "") return "REQUIRED";
  if (FORBIDDEN.test(v)) return "INVALID_CHARACTERS";
  if ([...v].length > MAX_LENGTH[field]) return "TOO_LONG";
  if (field === "email" && !EMAIL.test(v)) return "INVALID_EMAIL";
  return undefined;
}
