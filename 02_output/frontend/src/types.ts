// Shapes of docs/02_contracts/openapi.yaml used by the registration page.

export type RegistrationType = "EXTERNAL" | "STUDENT";

export type Category = "workshop" | "event" | "meal" | "other";

export type TextFieldName =
  | "firstName"
  | "lastName"
  | "email"
  | "organization"
  | "studyInstitution"
  | "studyProgramme"
  | "studentId";

export interface FormOption {
  id: string;
  name: string;
  availableTo: RegistrationType[];
}

export interface CategoryOptions {
  category: Category;
  maxSelections: number;
  options: FormOption[];
}

export interface Consent {
  id: string;
  text: string;
  mandatory: boolean;
}

export interface RegistrationFormData {
  conferenceName: string;
  captcha: { mode: "recaptcha" | "test"; siteKey: string | null };
  categories: CategoryOptions[];
  consents: Consent[];
}

export type RegistrationRequest = { type: RegistrationType } & Partial<
  Record<TextFieldName, string>
> & {
    optionIds: string[];
    consentIds: string[];
    captchaToken: string;
  };

export interface RegistrationCreated {
  registrationId: string;
  registeredAt: string;
  type: RegistrationType;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export interface ErrorResponse {
  error: string;
  message: string;
  fieldErrors: FieldError[];
}
