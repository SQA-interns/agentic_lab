/** Types of the REST contract (docs/02_contracts/openapi.yaml). */
export type RegistrationType = "EXTERNAL" | "STUDENT";
export type OptionCategory = "WORKSHOP" | "EVENT" | "MEAL" | "OTHER";

export interface ConferenceOption {
  id: string;
  name: string;
  category: OptionCategory;
  offeredTo: RegistrationType[];
}

export interface RegistrationSetup {
  conferenceName: string;
  consent: { id: string; text: string };
  recaptcha: { testMode: boolean; siteKey: string };
  options: ConferenceOption[];
}

export interface RegistrationRequest {
  type: RegistrationType;
  firstName: string;
  lastName: string;
  email: string;
  organization?: string;
  studyInstitution?: string;
  studyProgramme?: string;
  studentId?: string;
  optionIds: string[];
  consentGiven: boolean;
  recaptchaToken: string;
}

export interface FieldError {
  field: string;
  code: string;
}

export type SubmitResult =
  | { kind: "accepted"; registrationId: string; receivedAt: string }
  | { kind: "invalid"; errors: FieldError[] }
  | { kind: "duplicate" }
  | { kind: "failed" };
