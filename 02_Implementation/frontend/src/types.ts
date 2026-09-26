export type RegistrationType = "EXTERNAL" | "STUDENT";

export type OptionCategory = "WORKSHOP" | "EVENT" | "MEAL" | "OTHER";

export interface ConferenceOption {
  id: string;
  name: string;
  category: OptionCategory;
}

export interface ConsentDefinition {
  id: string;
  label: string;
  required: boolean;
}

export interface ConferenceConfig {
  options: ConferenceOption[];
  consents: ConsentDefinition[];
  recaptcha: { siteKey: string; testMode: boolean };
}

export type TextField =
  | "firstName"
  | "lastName"
  | "email"
  | "organization"
  | "studyInstitution"
  | "studyProgramme"
  | "studentId";

export type FormValues = Record<TextField, string>;

export interface RegistrationRequest extends Partial<FormValues> {
  type: RegistrationType;
  optionIds: string[];
  consentIds: string[];
  recaptchaToken: string;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ApiError {
  code: string;
  message: string;
  fieldErrors: FieldError[];
}

export type FieldErrors = Record<string, string>;
