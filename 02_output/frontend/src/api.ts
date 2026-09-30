import type { ClientConfig, FieldErrors, OptionsResponse, Registration } from "./types";

// The only way the frontend talks to the backend: the REST API under /api (AR-01).

export const GENERIC_ERROR = "The registration could not be sent. Please try again later.";

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(path, { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`GET ${path} failed with ${response.status}`);
  }
  return (await response.json()) as T;
}

export function fetchConfig(): Promise<ClientConfig> {
  return getJson<ClientConfig>("/api/config");
}

export function fetchOptions(): Promise<OptionsResponse> {
  return getJson<OptionsResponse>("/api/options");
}

export type SubmitResult =
  | { ok: true; registration: Registration }
  | { ok: false; fieldErrors: FieldErrors; message?: string };

type ErrorBody = { fieldErrors?: { field: string; message: string }[]; message?: string };

export async function submitRegistration(body: Record<string, unknown>): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch("/api/registrations", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(body),
    });
  } catch {
    return { ok: false, fieldErrors: {}, message: GENERIC_ERROR };
  }
  if (response.status === 201) {
    return { ok: true, registration: (await response.json()) as Registration };
  }
  if (response.status === 400 || response.status === 409) {
    const error = (await response.json().catch(() => ({}))) as ErrorBody;
    const fieldErrors: FieldErrors = {};
    for (const e of error.fieldErrors ?? []) {
      fieldErrors[e.field] ??= e.message;
    }
    return Object.keys(fieldErrors).length > 0
      ? { ok: false, fieldErrors }
      : { ok: false, fieldErrors, message: GENERIC_ERROR };
  }
  if (response.status === 429) {
    return {
      ok: false,
      fieldErrors: {},
      message: "Too many attempts. Please wait a minute and try again.",
    };
  }
  return { ok: false, fieldErrors: {}, message: GENERIC_ERROR };
}
