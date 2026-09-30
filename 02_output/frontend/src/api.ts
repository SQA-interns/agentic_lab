// REST client for docs/02_contracts/openapi.yaml.

export type GroupKey = 'workshops' | 'events' | 'meals' | 'other';
export type FormType = 'external' | 'student';

export interface CatalogOption {
  id: string;
  name: string;
}

export interface CatalogGroup {
  id: GroupKey;
  label: string;
  options: CatalogOption[];
}

export interface Catalog {
  conferenceTitle: string;
  captcha: { mode: 'stub' | 'recaptcha'; siteKey: string | null };
  consent: { id: string; text: string; required: boolean } | null;
  groups: CatalogGroup[];
}

export interface FieldError {
  field: string;
  code: string;
  message: string;
}

export interface Accepted {
  registrationId: string;
  clientRequestId: string;
  formType: FormType;
  status: 'ACCEPTED';
  acceptedAt: string;
}

export type SubmitResult =
  | { kind: 'accepted'; accepted: Accepted }
  | { kind: 'invalid'; errors: FieldError[] }
  | { kind: 'conflict' }
  | { kind: 'rate-limited' }
  | { kind: 'not-saved' };

export async function fetchCatalog(fetchImpl: typeof fetch = fetch): Promise<Catalog> {
  const response = await fetchImpl('/api/catalog', { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    throw new Error(`catalog request failed with ${String(response.status)}`);
  }
  return (await response.json()) as Catalog;
}

/** Sends a registration; only a 200/201 response counts as accepted (BR-05). */
export async function submitRegistration(
  form: FormType,
  payload: Record<string, unknown>,
  fetchImpl: typeof fetch = fetch,
): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetchImpl(`/api/registrations/${form}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(payload),
    });
  } catch {
    return { kind: 'not-saved' };
  }
  if (response.status === 200 || response.status === 201) {
    try {
      return { kind: 'accepted', accepted: (await response.json()) as Accepted };
    } catch {
      return { kind: 'not-saved' };
    }
  }
  if (response.status === 400) {
    try {
      const body = (await response.json()) as { errors?: FieldError[] };
      return { kind: 'invalid', errors: body.errors ?? [] };
    } catch {
      return { kind: 'not-saved' };
    }
  }
  if (response.status === 409) return { kind: 'conflict' };
  if (response.status === 429) return { kind: 'rate-limited' };
  return { kind: 'not-saved' };
}
