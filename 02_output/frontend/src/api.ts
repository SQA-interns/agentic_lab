import type { FormConfig } from './contract';

// The only module that talks to the backend, always through relative /api URLs (AR-01).

export interface RegistrationBody {
  type: string;
  optionIds: string[];
  consentGiven: boolean;
  captchaToken: string;
  [field: string]: string | string[] | boolean;
}

export type SubmitResult =
  | { kind: 'accepted'; id: string; acceptedAt: string }
  | { kind: 'invalid'; errors: { field: string; code: string }[] }
  | { kind: 'failed'; code: string };

export async function fetchFormConfig(): Promise<FormConfig> {
  const response = await fetch('/api/form-config', { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`form configuration: ${response.status}`);
  }
  return (await response.json()) as FormConfig;
}

export async function submitRegistration(body: RegistrationBody): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch('/api/registrations', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body),
    });
  } catch {
    return { kind: 'failed', code: 'GENERIC' };
  }
  const payload = (await response.json().catch(() => ({}))) as {
    id?: string;
    acceptedAt?: string;
    code?: string;
    errors?: { field: string; code: string }[];
  };
  if (response.status === 201 && payload.id && payload.acceptedAt) {
    return { kind: 'accepted', id: payload.id, acceptedAt: payload.acceptedAt };
  }
  if (response.status === 400 && payload.code === 'VALIDATION_FAILED' && payload.errors) {
    return { kind: 'invalid', errors: payload.errors };
  }
  const known = ['EMAIL_ALREADY_REGISTERED', 'RATE_LIMITED', 'STORAGE_UNAVAILABLE'];
  return {
    kind: 'failed',
    code: payload.code && known.includes(payload.code) ? payload.code : 'GENERIC',
  };
}
