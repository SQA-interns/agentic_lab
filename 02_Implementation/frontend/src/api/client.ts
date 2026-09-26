import type {
  ApiError,
  ConferenceConfig,
  RegistrationRequest,
  RegistrationType,
} from "../types";

export interface RegistrationSuccess {
  registrationId: string;
  type: RegistrationType;
}

export type SubmitResult =
  | { ok: true; data: RegistrationSuccess }
  | { ok: false; status: number; error: ApiError };

const GENERIC_ERROR: ApiError = {
  code: "NETWORK_ERROR",
  message:
    "Your registration could not be sent. Please check your connection and try again.",
  fieldErrors: [],
};

export async function fetchConference(): Promise<ConferenceConfig> {
  const response = await fetch("/api/conference", {
    headers: { Accept: "application/json" },
  });
  if (!response.ok) {
    throw new Error(
      `Conference configuration request failed (${response.status})`,
    );
  }
  return (await response.json()) as ConferenceConfig;
}

export async function submitRegistration(
  request: RegistrationRequest,
): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch("/api/registrations", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
      },
      body: JSON.stringify(request),
    });
  } catch {
    return { ok: false, status: 0, error: GENERIC_ERROR };
  }
  if (response.status === 201) {
    return { ok: true, data: (await response.json()) as RegistrationSuccess };
  }
  return {
    ok: false,
    status: response.status,
    error: await readError(response),
  };
}

async function readError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as Partial<ApiError>;
    return {
      code: body.code ?? "UNKNOWN_ERROR",
      message: body.message ?? GENERIC_ERROR.message,
      fieldErrors: Array.isArray(body.fieldErrors) ? body.fieldErrors : [],
    };
  } catch {
    return { ...GENERIC_ERROR, code: "UNKNOWN_ERROR" };
  }
}
