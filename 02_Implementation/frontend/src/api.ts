import type { Accepted, FieldErrorDto, FormConfig, OptionGroupKey, ParticipantType } from './types';

export interface RegistrationPayload {
  clientRequestId: string;
  fields: Record<string, string>;
  selections: Record<OptionGroupKey, string[]>;
  consents: Record<string, boolean>;
  captchaToken: string;
}

export type SubmitResult =
  | { kind: 'accepted'; data: Accepted }
  | { kind: 'rejected'; status: number; code: string; errors: FieldErrorDto[] }
  | { kind: 'network-error' };

export async function fetchFormConfig(signal?: AbortSignal): Promise<FormConfig> {
  const response = await fetch('/api/form-config', {
    signal,
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new Error(`Form configuration unavailable (${response.status})`);
  }
  return (await response.json()) as FormConfig;
}

/** Success is reported only for backend 201 (accepted) or 200 (idempotent replay). */
export async function submitRegistration(
  type: ParticipantType,
  payload: RegistrationPayload,
): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch(`/api/registrations/${type}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({
        clientRequestId: payload.clientRequestId,
        ...payload.fields,
        selections: payload.selections,
        consents: payload.consents,
        captchaToken: payload.captchaToken,
      }),
    });
  } catch {
    return { kind: 'network-error' };
  }
  const body: unknown = await response.json().catch(() => null);
  if ((response.status === 201 || response.status === 200) && isAccepted(body)) {
    return { kind: 'accepted', data: body };
  }
  const problem = (body ?? {}) as { code?: string; errors?: FieldErrorDto[] };
  return {
    kind: 'rejected',
    status: response.status,
    code: problem.code ?? 'UNKNOWN',
    errors: Array.isArray(problem.errors) ? problem.errors : [],
  };
}

function isAccepted(body: unknown): body is Accepted {
  return (
    typeof body === 'object' &&
    body !== null &&
    typeof (body as Accepted).registrationId === 'string'
  );
}
