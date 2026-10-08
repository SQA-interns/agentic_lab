// Client for the REST contract (docs/02_contracts/api.openapi.yaml). Only relative /api paths (AR-01).

export type RegistrationType = "EXTERNAL" | "STUDENT";
export type Category = "WORKSHOP" | "EVENT" | "MEAL" | "OTHER";

export interface ConferenceOption {
  id: string;
  name: string;
  category: Category;
  registrationTypes: RegistrationType[];
}

export interface ConsentConfig {
  id: string;
  text: string;
  mandatory: boolean;
}

export interface FormConfig {
  conferenceName: string;
  recaptcha: { siteKey: string; testMode: boolean };
  categories: { category: Category; maxSelections: number }[];
  options: ConferenceOption[];
  consents: ConsentConfig[];
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
  consentIds: string[];
  recaptchaToken: string;
}

export interface FieldError {
  field: string;
  code: string;
}

export type SubmitResult =
  | { kind: "accepted"; id: string }
  | { kind: "invalid"; errors: FieldError[] }
  | { kind: "failed"; title: string };

export const GENERIC_FAILURE = "Registration could not be processed";

export async function loadForm(): Promise<FormConfig> {
  const response = await fetch("/api/form", { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`form configuration unavailable (${response.status})`);
  }
  return (await response.json()) as FormConfig;
}

export async function submitRegistration(request: RegistrationRequest): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch("/api/registrations", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(request),
    });
  } catch {
    return { kind: "failed", title: GENERIC_FAILURE };
  }
  const body = await readJson(response);
  if (response.status === 201) {
    return { kind: "accepted", id: String(body?.id ?? "") };
  }
  if (response.status === 400 && Array.isArray(body?.errors)) {
    return { kind: "invalid", errors: body.errors as FieldError[] };
  }
  const title = typeof body?.title === "string" && response.status < 500 ? body.title : null;
  return { kind: "failed", title: title ?? GENERIC_FAILURE };
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
async function readJson(response: Response): Promise<any> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}
