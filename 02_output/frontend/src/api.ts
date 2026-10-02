// The only place that talks to the backend, and only under /api (AR-01).
// Shapes follow docs/02_contracts/openapi.yaml.

export type RegistrationType = "EXTERNAL" | "STUDENT";
export type OptionCategory = "workshop" | "event" | "meal" | "other";
export type FieldName =
  | "firstName"
  | "lastName"
  | "email"
  | "organization"
  | "studyInstitution"
  | "studyProgramme"
  | "studentId";

export interface ConferenceOption {
  id: string;
  name: string;
  category: OptionCategory;
}

export interface FormConfig {
  conferenceName: string;
  consent: { id: string; text: string };
  captcha: { mode: "recaptcha" | "test"; siteKey: string };
}

export interface RegistrationRequest {
  type: RegistrationType;
  optionIds: string[];
  consent: boolean;
  captchaToken: string;
  [field: string]: unknown;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export type SubmitResult =
  | { kind: "accepted" }
  | { kind: "rejected"; errors: FieldError[] }
  | { kind: "tooManyRequests" }
  | { kind: "notReceived" };

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`GET ${path} answered ${response.status}`);
  }
  return (await response.json()) as T;
}

export function loadFormConfig(): Promise<FormConfig> {
  return getJson<FormConfig>("/api/form-config");
}

export async function loadOptions(): Promise<ConferenceOption[]> {
  return (await getJson<{ options: ConferenceOption[] }>("/api/options")).options;
}

function isFieldError(value: unknown): value is FieldError {
  const candidate = value as Partial<FieldError> | null;
  return typeof candidate?.field === "string" && typeof candidate.code === "string";
}

/** Sends the registration; every outcome, including no answer at all, is a result. */
export async function submitRegistration(request: RegistrationRequest): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch("/api/registrations", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(request),
    });
  } catch {
    return { kind: "notReceived" };
  }
  if (response.status === 201) {
    return { kind: "accepted" };
  }
  if (response.status === 429) {
    return { kind: "tooManyRequests" };
  }
  if (response.status === 400) {
    try {
      const problem = (await response.json()) as { errors?: unknown };
      const errors = Array.isArray(problem.errors) ? problem.errors.filter(isFieldError) : [];
      if (errors.length > 0) {
        return { kind: "rejected", errors };
      }
    } catch {
      // An unreadable rejection is treated like any other failure below.
    }
  }
  return { kind: "notReceived" };
}
