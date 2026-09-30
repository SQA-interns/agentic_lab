// Client for the backend REST API under /api (AR-01; docs/02_contracts/openapi.yaml).

export type RegistrationType = 'EXTERNAL' | 'STUDENT';
export type Category = 'WORKSHOP' | 'EVENT' | 'MEAL' | 'OTHER';

export interface ClientConfig {
  conferenceName: string;
  captchaTestMode: boolean;
  recaptchaSiteKey: string;
}

export interface Option {
  id: string;
  name: string;
  category: Category;
}

export interface Consent {
  id: string;
  text: string;
  mandatory: boolean;
}

export interface OptionsResponse {
  type: RegistrationType;
  options: Option[];
  consents: Consent[];
  categoryLimits: Partial<Record<Category, number>>;
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
  captchaToken: string;
}

export interface RegistrationConfirmation {
  reference: string;
  type: RegistrationType;
  firstName: string;
  lastName: string;
  email: string;
  options: Option[];
  submittedAt: string;
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ErrorResponse {
  message: string;
  errors: FieldError[];
}

export type SubmitResult =
  | { ok: true; confirmation: RegistrationConfirmation }
  | { ok: false; status: number; error: ErrorResponse };

const GENERIC_ERROR = 'The registration could not be sent. Please try again later.';

async function getJson<T>(url: string): Promise<T> {
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`Request failed with status ${response.status}`);
  }
  return (await response.json()) as T;
}

export function fetchConfig(): Promise<ClientConfig> {
  return getJson<ClientConfig>('/api/config');
}

export function fetchOptions(type: RegistrationType): Promise<OptionsResponse> {
  return getJson<OptionsResponse>(`/api/options?type=${type}`);
}

async function readError(response: Response): Promise<ErrorResponse> {
  try {
    const body = (await response.json()) as Partial<ErrorResponse>;
    return {
      message: typeof body.message === 'string' && body.message ? body.message : GENERIC_ERROR,
      errors: Array.isArray(body.errors) ? body.errors : [],
    };
  } catch {
    return { message: GENERIC_ERROR, errors: [] };
  }
}

export async function submitRegistration(request: RegistrationRequest): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch('/api/registrations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(request),
    });
  } catch {
    return { ok: false, status: 0, error: { message: GENERIC_ERROR, errors: [] } };
  }
  if (response.status === 201) {
    return { ok: true, confirmation: (await response.json()) as RegistrationConfirmation };
  }
  return { ok: false, status: response.status, error: await readError(response) };
}
