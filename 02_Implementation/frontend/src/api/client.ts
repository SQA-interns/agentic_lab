// Typed REST client for docs/contracts/openapi.yaml.

export type OptionCategory = "WORKSHOP" | "EVENT" | "MEAL" | "OTHER";
export type RegistrationType = "EXTERNAL" | "STUDENT";

export interface ConferenceOption {
  id: string;
  category: OptionCategory;
  name: string;
}

export interface ClientConfig {
  recaptchaSiteKey: string;
  recaptchaTestMode: boolean;
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
  personalDataConsent: boolean;
  recaptchaToken: string;
}

export interface RegistrationResponse {
  registrationId: string;
  submittedAt: string;
  type: RegistrationType;
  firstName: string;
  lastName: string;
  email: string;
  organization?: string | null;
  studyInstitution?: string | null;
  studyProgramme?: string | null;
  studentId?: string | null;
  options: ConferenceOption[];
}

export interface ServerFieldError {
  field: string;
  code: string;
  message: string;
}

export type SubmitResult =
  | { kind: "created"; registration: RegistrationResponse }
  | { kind: "rejected"; errors: ServerFieldError[] }
  | { kind: "failed" };

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path, {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new Error(`GET ${path} failed with ${response.status}`);
  }
  return (await response.json()) as T;
}

export function fetchOptions(): Promise<ConferenceOption[]> {
  return getJson<ConferenceOption[]>("/api/options");
}

export function fetchClientConfig(): Promise<ClientConfig> {
  return getJson<ClientConfig>("/api/config");
}

export async function submitRegistration(
  request: RegistrationRequest,
): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch("/api/registrations", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(request),
    });
  } catch {
    return { kind: "failed" };
  }
  if (response.status === 201) {
    return {
      kind: "created",
      registration: (await response.json()) as RegistrationResponse,
    };
  }
  if (response.status === 400) {
    try {
      const problem = (await response.json()) as {
        errors?: ServerFieldError[];
      };
      if (Array.isArray(problem.errors) && problem.errors.length > 0) {
        return { kind: "rejected", errors: problem.errors };
      }
    } catch {
      // fall through to a general failure
    }
  }
  return { kind: "failed" };
}
