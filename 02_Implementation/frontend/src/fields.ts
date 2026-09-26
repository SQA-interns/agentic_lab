import type { OptionCategory, RegistrationType, TextField } from "./types";

export interface FieldDefinition {
  name: TextField;
  label: string;
  maxLength: number;
  inputType: "text" | "email";
  autoComplete?: string;
}

const common: FieldDefinition[] = [
  {
    name: "firstName",
    label: "First name",
    maxLength: 100,
    inputType: "text",
    autoComplete: "given-name",
  },
  {
    name: "lastName",
    label: "Last name",
    maxLength: 100,
    inputType: "text",
    autoComplete: "family-name",
  },
  {
    name: "email",
    label: "Email",
    maxLength: 254,
    inputType: "email",
    autoComplete: "email",
  },
];

export const FIELDS_BY_TYPE: Record<RegistrationType, FieldDefinition[]> = {
  EXTERNAL: [
    ...common,
    {
      name: "organization",
      label: "Organization / institution",
      maxLength: 200,
      inputType: "text",
      autoComplete: "organization",
    },
  ],
  STUDENT: [
    ...common,
    {
      name: "studyInstitution",
      label: "Study institution",
      maxLength: 200,
      inputType: "text",
    },
    {
      name: "studyProgramme",
      label: "Study programme",
      maxLength: 200,
      inputType: "text",
    },
    {
      name: "studentId",
      label: "Student ID",
      maxLength: 50,
      inputType: "text",
    },
  ],
};

export const TYPE_LABELS: Record<RegistrationType, string> = {
  EXTERNAL: "External participant",
  STUDENT: "Student",
};

export const CATEGORY_ORDER: OptionCategory[] = [
  "WORKSHOP",
  "EVENT",
  "MEAL",
  "OTHER",
];

export const CATEGORY_LABELS: Record<OptionCategory, string> = {
  WORKSHOP: "Workshops",
  EVENT: "Events",
  MEAL: "Meals",
  OTHER: "Other activities",
};
