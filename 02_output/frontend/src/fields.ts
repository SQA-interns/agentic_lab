import type { RegistrationType } from "./types";

// The fixed participant fields per registration type (BR-01, docs/02_contracts/ui.md item 3).

export type FieldName =
  | "firstName"
  | "lastName"
  | "email"
  | "organization"
  | "studyInstitution"
  | "studyProgramme"
  | "studentId";

export type FieldDefinition = {
  name: FieldName;
  label: string;
  maxLength: number;
  inputType?: "email";
  autoComplete?: string;
};

const COMMON: FieldDefinition[] = [
  { name: "firstName", label: "First name", maxLength: 100, autoComplete: "given-name" },
  { name: "lastName", label: "Last name", maxLength: 100, autoComplete: "family-name" },
  { name: "email", label: "Email", maxLength: 254, inputType: "email", autoComplete: "email" },
];

export const FIELDS: Record<RegistrationType, FieldDefinition[]> = {
  EXTERNAL: [
    ...COMMON,
    {
      name: "organization",
      label: "Organization / institution",
      maxLength: 200,
      autoComplete: "organization",
    },
  ],
  STUDENT: [
    ...COMMON,
    { name: "studyInstitution", label: "Study institution", maxLength: 200 },
    { name: "studyProgramme", label: "Study programme", maxLength: 200 },
    { name: "studentId", label: "Student ID", maxLength: 50 },
  ],
};

export type FieldValues = Record<FieldName, string>;

export const EMPTY_VALUES: FieldValues = {
  firstName: "",
  lastName: "",
  email: "",
  organization: "",
  studyInstitution: "",
  studyProgramme: "",
  studentId: "",
};
