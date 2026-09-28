import type { RegistrationType } from "./api";

export type FieldName =
  | "firstName"
  | "lastName"
  | "email"
  | "organization"
  | "studyInstitution"
  | "studyProgramme"
  | "studentId";

export interface FieldDefinition {
  name: FieldName;
  label: string;
  maxLength: number;
  type: "text" | "email";
  autoComplete?: string;
}

const COMMON_FIELDS: FieldDefinition[] = [
  {
    name: "firstName",
    label: "First name",
    maxLength: 100,
    type: "text",
    autoComplete: "given-name",
  },
  {
    name: "lastName",
    label: "Last name",
    maxLength: 100,
    type: "text",
    autoComplete: "family-name",
  },
  {
    name: "email",
    label: "Email",
    maxLength: 254,
    type: "email",
    autoComplete: "email",
  },
];

/** Fixed participant fields per registration type (FORM_SCHEMA). */
export const FIELDS: Record<RegistrationType, FieldDefinition[]> = {
  EXTERNAL: [
    ...COMMON_FIELDS,
    {
      name: "organization",
      label: "Organization / institution",
      maxLength: 200,
      type: "text",
      autoComplete: "organization",
    },
  ],
  STUDENT: [
    ...COMMON_FIELDS,
    {
      name: "studyInstitution",
      label: "Study institution",
      maxLength: 200,
      type: "text",
    },
    {
      name: "studyProgramme",
      label: "Study programme",
      maxLength: 200,
      type: "text",
    },
    { name: "studentId", label: "Student ID", maxLength: 50, type: "text" },
  ],
};
