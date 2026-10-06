// REST client of the backend (docs/02_contracts/api.openapi.yaml); only relative /api paths (AR-01).

export type RegistrationType = "external" | "student";
export type OptionCategory = "workshop" | "event" | "meal" | "other";

export interface FormOption {
  id: string;
  name: string;
  category: OptionCategory;
  offeredTo: RegistrationType[];
}

export interface FormConsent {
  id: string;
  text: string;
  mandatory: boolean;
}

export interface FormConfig {
  conferenceName: string;
  options: FormOption[];
  consents: FormConsent[];
  captcha: { mode: "live" | "test"; siteKey?: string };
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
  consents: string[];
  captchaToken: string;
}

export interface Confirmation {
  registrationId: string;
  type: RegistrationType;
  firstName: string;
  lastName: string;
  email: string;
  options: { id: string; name: string; category: OptionCategory }[];
  receivedAt: string;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export type SubmitResult =
  | { kind: "accepted"; confirmation: Confirmation }
  | { kind: "rejected"; status: number; errors: FieldError[] };

export async function fetchFormConfig(): Promise<FormConfig> {
  const response = await fetch("/api/form-config", {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new Error(`form configuration unavailable (${response.status})`);
  }
  return (await response.json()) as FormConfig;
}

export async function submitRegistration(
  request: RegistrationRequest,
): Promise<SubmitResult> {
  const response = await fetch("/api/registrations", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "application/json",
    },
    body: JSON.stringify(request),
  });
  if (response.status === 201) {
    return {
      kind: "accepted",
      confirmation: (await response.json()) as Confirmation,
    };
  }
  return {
    kind: "rejected",
    status: response.status,
    errors: await fieldErrors(response),
  };
}

/** Field errors of a problem response; anything else in the body is never shown (ES-07). */
async function fieldErrors(response: Response): Promise<FieldError[]> {
  try {
    const body: unknown = await response.json();
    const errors = (body as { errors?: unknown }).errors;
    if (!Array.isArray(errors)) {
      return [];
    }
    return errors.filter(
      (e): e is FieldError =>
        typeof e === "object" &&
        e !== null &&
        typeof (e as FieldError).field === "string" &&
        typeof (e as FieldError).code === "string" &&
        typeof (e as FieldError).message === "string",
    );
  } catch {
    return [];
  }
}
