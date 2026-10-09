import type {
  ErrorResponse,
  RegistrationCreated,
  RegistrationFormData,
  RegistrationRequest,
} from "./types";

// The only backend access of the frontend: relative /api URLs (AR-01).

export type SubmitResult =
  | { kind: "created"; created: RegistrationCreated }
  | { kind: "rejected"; error: ErrorResponse }
  | { kind: "failed"; errorCode: string | null };

export async function loadRegistrationForm(): Promise<RegistrationFormData> {
  const response = await fetch("/api/registration-form", {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new Error(`Form data request failed with ${response.status}`);
  }
  return (await response.json()) as RegistrationFormData;
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
    return { kind: "failed", errorCode: null };
  }
  const body: unknown = await response.json().catch(() => null);
  if (response.status === 201 && isCreated(body)) {
    return { kind: "created", created: body };
  }
  if (isErrorResponse(body)) {
    if ((response.status === 400 || response.status === 409) && body.fieldErrors.length > 0) {
      return { kind: "rejected", error: body };
    }
    return { kind: "failed", errorCode: body.error };
  }
  return { kind: "failed", errorCode: null };
}

function isCreated(body: unknown): body is RegistrationCreated {
  return typeof body === "object" && body !== null && "registrationId" in body;
}

function isErrorResponse(body: unknown): body is ErrorResponse {
  return (
    typeof body === "object" &&
    body !== null &&
    "error" in body &&
    "fieldErrors" in body &&
    Array.isArray((body as ErrorResponse).fieldErrors)
  );
}
