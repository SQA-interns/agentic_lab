import type { RegistrationRequest, RegistrationSetup, SubmitResult } from "./types";

// AR-01: the only module that talks to the backend, always through relative /api paths.

export async function fetchSetup(): Promise<RegistrationSetup> {
  const response = await fetch("/api/options", { headers: { Accept: "application/json" } });
  if (!response.ok) {
    throw new Error(`options request failed with ${response.status}`);
  }
  return (await response.json()) as RegistrationSetup;
}

export async function submitRegistration(request: RegistrationRequest): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch("/api/registrations", {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify(request),
    });
  } catch {
    return { kind: "failed" };
  }
  try {
    if (response.status === 201) {
      const body = (await response.json()) as { registrationId: string; receivedAt: string };
      return { kind: "accepted", ...body };
    }
    if (response.status === 409) {
      return { kind: "duplicate" };
    }
    if (response.status === 400) {
      const body = (await response.json()) as { errors?: SubmitResultErrors };
      if (Array.isArray(body.errors) && body.errors.length > 0) {
        return { kind: "invalid", errors: body.errors };
      }
    }
  } catch {
    return { kind: "failed" };
  }
  return { kind: "failed" };
}

type SubmitResultErrors = { field: string; code: string }[];
