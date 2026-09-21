/**
 * Backend REST client (specification § 4, § 9.3).
 *
 * The only module that talks to the API. It converts transport outcomes into a small
 * discriminated union so the page can distinguish success, validation rejection,
 * anti-automation rejection, rate limiting and technical failure — which is exactly the
 * distinction the confirmation rules depend on (AC-004-01, AC-004-04, AC-004-05).
 */

export const API_BASE_URL: string =
  (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? '/api';

export type RegistrationVariant = 'external' | 'student';

export interface FieldRule {
  required: boolean;
  maxLength: number;
  minLength?: number;
  format?: 'email';
  pattern?: string;
}

export interface ConfigOption {
  id: string;
  displayName: string;
  description?: string;
}

export interface ConfigOptionGroup {
  id: string;
  displayName: string;
  options: ConfigOption[];
}

export interface ConsentDefinition {
  id: string;
  required: boolean;
  text: string;
}

export interface RegistrationConfig {
  conferenceName: string;
  variant: RegistrationVariant;
  fields: string[];
  fieldRules: Record<string, FieldRule>;
  consents: ConsentDefinition[];
  optionGroups: ConfigOptionGroup[];
  formToken: string;
  formTokenTtlSeconds: number;
}

export interface SelectedOptionSummary {
  optionId: string;
  group: string;
  displayName: string;
}

export interface RegistrationSuccess {
  reference: string;
  variant: RegistrationVariant;
  email: string;
  createdAt: string;
  selectedOptions: SelectedOptionSummary[];
  confirmationEmailQueuedTo: string;
}

export interface ApiFieldError {
  field: string;
  code: string;
  message: string;
}

export type RegistrationOutcome =
  | { kind: 'created'; registration: RegistrationSuccess }
  | { kind: 'validation'; fields: ApiFieldError[]; message: string }
  | { kind: 'anti-automation'; message: string }
  | { kind: 'rate-limited'; message: string }
  | { kind: 'technical'; message: string };

export interface RegistrationRequest {
  variant: RegistrationVariant;
  firstName: string;
  lastName: string;
  email: string;
  organization?: string;
  studyInstitution?: string;
  studyProgramme?: string;
  studentId?: string;
  selectedOptionIds: string[];
  consents: Record<string, boolean>;
  formToken: string;
  website: string;
}

export async function fetchRegistrationConfig(
  variant: RegistrationVariant,
): Promise<RegistrationConfig> {
  const response = await fetch(`${API_BASE_URL}/registration-config?variant=${variant}`, {
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`The registration form could not be loaded (status ${response.status}).`);
  }
  return (await response.json()) as RegistrationConfig;
}

export async function submitRegistration(
  request: RegistrationRequest,
): Promise<RegistrationOutcome> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}/registrations`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(request),
    });
  } catch {
    // Network failure or timeout: a technical problem, never a validation problem.
    return {
      kind: 'technical',
      message:
        'The registration could not be sent because the service could not be reached. Your data has been kept — please try again.',
    };
  }

  if (response.status === 201) {
    return { kind: 'created', registration: (await response.json()) as RegistrationSuccess };
  }

  return mapErrorResponse(response.status, await safeJson(response));
}

const TECHNICAL_FAILURE: RegistrationOutcome = {
  kind: 'technical',
  message:
    'The registration could not be processed because of a technical problem. Your data has been kept — please try again.',
};

function mapErrorResponse(status: number, body: ApiErrorBody | null): RegistrationOutcome {
  const error = body?.error;

  if (status === 429) {
    return {
      kind: 'rate-limited',
      message: error?.message ?? 'Too many attempts. Please try again later.',
    };
  }
  if (status !== 400) {
    return TECHNICAL_FAILURE;
  }

  switch (error?.code) {
    case 'VALIDATION_ERROR':
      return {
        kind: 'validation',
        fields: Array.isArray(error.fields) ? (error.fields as ApiFieldError[]) : [],
        message: error.message ?? 'Some fields are invalid.',
      };
    case 'ANTI_AUTOMATION_FAILED':
      return {
        kind: 'anti-automation',
        message: error.message ?? 'Please reload the form and try again.',
      };
    default:
      return TECHNICAL_FAILURE;
  }
}

interface ApiErrorBody {
  error?: { code?: string; message?: string; fields?: unknown };
}

async function safeJson(response: Response): Promise<ApiErrorBody | null> {
  try {
    return (await response.json()) as ApiErrorBody;
  } catch {
    return null;
  }
}
