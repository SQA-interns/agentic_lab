export type OptionCategory = "WORKSHOP" | "EVENT" | "MEAL" | "OTHER";

export type RegistrationType = "EXTERNAL" | "STUDENT";

export interface ConferenceOption {
  id: string;
  name: string;
  category: OptionCategory;
}

export interface Consent {
  id: string;
  text: string;
  mandatory: boolean;
}

export interface CaptchaConfig {
  mode: "TEST" | "RECAPTCHA";
  siteKey?: string;
}

export interface FormConfig {
  options: ConferenceOption[];
  consents: Consent[];
  captcha: CaptchaConfig;
}

export interface RegistrationRequest {
  firstName: string;
  lastName: string;
  email: string;
  organization?: string;
  studyInstitution?: string;
  studyProgramme?: string;
  studentId?: string;
  optionIds: string[];
  consents: Record<string, boolean>;
  captchaToken: string;
}

export interface RegistrationResponse {
  registrationId: string;
  registrationType: RegistrationType;
  createdAt: string;
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export interface ErrorBody {
  error: string;
  message?: string;
  fieldErrors?: FieldError[];
}

export type SubmitResult =
  | { kind: "success"; registration: RegistrationResponse }
  | { kind: "invalid"; error: ErrorBody }
  | { kind: "failed" };

export async function fetchFormConfig(): Promise<FormConfig> {
  const response = await fetch("/api/form-config", {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new Error(`Form configuration unavailable (${response.status})`);
  }
  return (await response.json()) as FormConfig;
}

export async function submitRegistration(
  type: RegistrationType,
  request: RegistrationRequest,
): Promise<SubmitResult> {
  const path =
    type === "STUDENT"
      ? "/api/registrations/student"
      : "/api/registrations/external";
  let response: Response;
  try {
    response = await fetch(path, {
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
      kind: "success",
      registration: (await response.json()) as RegistrationResponse,
    };
  }
  if (response.status === 400) {
    try {
      return { kind: "invalid", error: (await response.json()) as ErrorBody };
    } catch {
      return { kind: "failed" };
    }
  }
  return { kind: "failed" };
}
