/** Typed client for docs/02_contracts/openapi.yaml; the backend is reached only under /api (AR-01). */

export type RegistrationType = "EXTERNAL" | "STUDENT";
export type Category = "WORKSHOP" | "EVENT" | "MEAL" | "OTHER";

export interface Option {
  id: string;
  displayName: string;
  category: Category;
  availableTo: RegistrationType[];
}

export interface FormConfig {
  conferenceName: string;
  categories: { id: Category; maxSelections?: number }[];
  options: Option[];
  consents: { id: string; text: string }[];
  antiAutomation: { mode: "live" | "test"; siteKey?: string };
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
  antiAutomationToken: string;
}

export interface RegistrationAccepted {
  id: string;
  type: RegistrationType;
  firstName: string;
  lastName: string;
  email: string;
  options: { id: string; displayName: string; category: Category }[];
  registeredAt: string;
}

export interface ApiError {
  code: string;
  message: string;
  fieldErrors?: { field: string; code: string; message: string }[];
}

export type SubmitResult =
  | { kind: "accepted"; registration: RegistrationAccepted }
  | { kind: "rejected"; error: ApiError }
  | { kind: "unreachable" };

export async function getFormConfig(): Promise<FormConfig> {
  const response = await fetch("/api/form-config", { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`form configuration: HTTP ${response.status}`);
  }
  return (await response.json()) as FormConfig;
}

export async function submitRegistration(body: RegistrationRequest): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch("/api/registrations", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(body),
    });
  } catch {
    return { kind: "unreachable" };
  }
  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    return { kind: "unreachable" };
  }
  if (response.status === 201) {
    return { kind: "accepted", registration: payload as RegistrationAccepted };
  }
  const error = payload as Partial<ApiError>;
  if (typeof error.code !== "string" || typeof error.message !== "string") {
    return { kind: "unreachable" };
  }
  return { kind: "rejected", error: error as ApiError };
}
