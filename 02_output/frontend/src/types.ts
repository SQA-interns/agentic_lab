// Shapes of the REST API (docs/02_contracts/openapi.json).

export type Category = "workshop" | "event" | "meal" | "other";

export type ConferenceOption = { id: string; name: string; category: Category };

export type Consent = { id: string; text: string; required: boolean };

export type OptionsResponse = { options: ConferenceOption[]; consents: Consent[] };

export type ClientConfig = {
  recaptchaSiteKey: string;
  recaptchaTestMode: boolean;
  conferenceName: string;
};

export type RegistrationType = "EXTERNAL" | "STUDENT";

export type Registration = {
  id: string;
  type: RegistrationType;
  submittedAt: string;
  firstName: string;
  lastName: string;
  email: string;
  organization?: string;
  studyInstitution?: string;
  studyProgramme?: string;
  studentId?: string;
  options: ConferenceOption[];
  consents: { id: string; givenAt: string }[];
};

/** Field name → message shown next to that field. */
export type FieldErrors = Record<string, string>;
