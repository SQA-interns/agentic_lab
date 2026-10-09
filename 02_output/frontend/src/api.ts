// Client for docs/02_contracts/registration-api.openapi.yaml (AR-01: only /api).

export type RegistrationType = "EXTERNAL" | "STUDENT";
export type Category = "WORKSHOP" | "EVENT" | "MEAL" | "OTHER";
export type FieldName =
  | "firstName"
  | "lastName"
  | "email"
  | "organization"
  | "studyInstitution"
  | "studyProgramme"
  | "studentId";

export interface FormDefinition {
  type: RegistrationType;
  fields: Array<{ name: FieldName; maxLength: number }>;
  categories: Array<{
    category: Category;
    maxSelections: number;
    options: Array<{ id: string; name: string }>;
  }>;
  consents: Array<{ id: string; text: string; mandatory: boolean }>;
  recaptcha: { mode: "GOOGLE" | "TEST"; siteKey?: string };
}

export interface FieldError {
  field: string;
  code: string;
  consentId?: string;
}

export interface Accepted {
  id: string;
  type: RegistrationType;
  firstName: string;
  lastName: string;
  selectedOptions: Array<{ id: string; name: string; category: Category }>;
}

export type SubmitResult =
  | { kind: "accepted"; registration: Accepted }
  | { kind: "rejected"; errors: FieldError[] }
  | { kind: "rateLimited" }
  | { kind: "failed" };

export interface RegistrationRequest {
  type: RegistrationType;
  values: Partial<Record<FieldName, string>>;
  optionIds: string[];
  consentIds: string[];
  recaptchaToken: string;
}

export async function loadForm(
  type: RegistrationType,
): Promise<FormDefinition> {
  const response = await fetch(`/api/registration-form/${type.toLowerCase()}`, {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new Error(`form ${response.status}`);
  }
  return (await response.json()) as FormDefinition;
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
      body: JSON.stringify({
        type: request.type,
        ...request.values,
        optionIds: request.optionIds,
        consentIds: request.consentIds,
        recaptchaToken: request.recaptchaToken,
      }),
    });
  } catch {
    return { kind: "failed" };
  }
  if (response.status === 201) {
    return {
      kind: "accepted",
      registration: (await response.json()) as Accepted,
    };
  }
  if (response.status === 400 || response.status === 409) {
    try {
      const body = (await response.json()) as { errors?: FieldError[] };
      if (Array.isArray(body.errors) && body.errors.length > 0) {
        return { kind: "rejected", errors: body.errors };
      }
    } catch {
      // fall through to a general failure
    }
    return { kind: "failed" };
  }
  if (response.status === 429) {
    return { kind: "rateLimited" };
  }
  return { kind: "failed" };
}
