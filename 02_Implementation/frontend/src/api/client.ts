import type { ConferenceOption, RegistrationResponse, RegistrationType } from '../types';

/** Error carrying the backend's uniform error body (specification §4.2). */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Readonly<Record<string, string>>;

  constructor(
    status: number,
    code: string,
    message: string,
    fieldErrors: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

interface ErrorBody {
  error?: unknown;
  message?: unknown;
  fieldErrors?: unknown;
}

function isRecordOfStrings(value: unknown): value is Record<string, string> {
  return (
    typeof value === 'object' &&
    value !== null &&
    Object.values(value).every((v) => typeof v === 'string')
  );
}

async function toApiError(response: Response): Promise<ApiError> {
  let body: ErrorBody = {};
  try {
    body = (await response.json()) as ErrorBody;
  } catch {
    // Non-JSON error (e.g. proxy error page) – fall through to generic error.
  }
  const code = typeof body.error === 'string' ? body.error : 'HTTP_' + String(response.status);
  const message =
    typeof body.message === 'string' ? body.message : 'The request could not be completed.';
  const fieldErrors = isRecordOfStrings(body.fieldErrors) ? body.fieldErrors : {};
  return new ApiError(response.status, code, message, fieldErrors);
}

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers);
  headers.set('Accept', 'application/json');
  let response: Response;
  try {
    response = await fetch(url, { ...init, headers });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', 'The server could not be reached.');
  }
  if (!response.ok) {
    throw await toApiError(response);
  }
  return (await response.json()) as T;
}

export function fetchOptions(type: RegistrationType): Promise<ConferenceOption[]> {
  return request<ConferenceOption[]>(`/api/options?type=${type}`);
}

export async function fetchFormToken(): Promise<string> {
  const body = await request<{ token: string }>('/api/form-token');
  return body.token;
}

export type RegistrationPayload = Record<string, string | boolean | string[]>;

export function submitRegistration(
  type: RegistrationType,
  payload: RegistrationPayload,
): Promise<RegistrationResponse> {
  const path = type === 'EXTERNAL' ? 'external' : 'student';
  return request<RegistrationResponse>(`/api/registrations/${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  });
}
