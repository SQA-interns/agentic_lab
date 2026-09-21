/**
 * Component tests — registration page behaviour in a DOM (specification § 9, US-004).
 *
 * Level justification: the confirmation rule (AC-004-01), the error rendering
 * (AC-004-04), the double-submit guard (AC-004-06) and the unchecked consent (AC-G-08)
 * are all properties of the rendered document. A jsdom component test observes exactly
 * that, without needing a browser, while the backend is replaced by scripted responses
 * so each documented outcome can be produced on demand.
 */
import { beforeEach, afterEach, describe, expect, it, vi } from 'vitest';

import { initRegistrationPage } from '../src/registrationPage.js';

const CONFIG = {
  conferenceName: 'Test Conference',
  variant: 'external',
  fields: ['firstName', 'lastName', 'email', 'organization'],
  fieldRules: {
    firstName: { required: true, maxLength: 100 },
    lastName: { required: true, maxLength: 100 },
    email: { required: true, maxLength: 254, format: 'email' },
    organization: { required: true, maxLength: 200 },
  },
  consents: [{ id: 'privacy', required: true, text: 'I agree to the processing of my personal data.' }],
  optionGroups: [
    { id: 'workshops', displayName: 'Workshops', options: [{ id: 'workshop-ai', displayName: 'Workshop: AI' }] },
    { id: 'events', displayName: 'Events', options: [] },
    { id: 'meals', displayName: 'Meals', options: [{ id: 'meal-lunch', displayName: 'Lunch' }] },
    { id: 'other', displayName: 'Other', options: [] },
  ],
  formToken: 'test-token',
  formTokenTtlSeconds: 1800,
};

const PAGE_HTML = `
  <div id="form-summary" class="summary" hidden></div>
  <section id="confirmation" tabindex="-1" role="status" hidden></section>
  <form id="registration-form" novalidate hidden>
    <span id="conference-name"></span>
    <div id="fixed-fields"></div>
    <div id="option-groups"></div>
    <div id="consents"></div>
    <div class="honeypot" aria-hidden="true">
      <input id="website" name="website" type="text" tabindex="-1" />
    </div>
    <button id="submit-button" type="submit">Submit registration</button>
  </form>
`;

interface ScriptedResponse {
  status: number;
  body: unknown;
}

function scriptBackend(submitResponse: ScriptedResponse | Error): void {
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) => {
      if (url.includes('registration-config')) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve(CONFIG) });
      }
      if (submitResponse instanceof Error) {
        return Promise.reject(submitResponse);
      }
      return Promise.resolve({
        ok: submitResponse.status < 400,
        status: submitResponse.status,
        json: () => Promise.resolve(submitResponse.body),
      });
    }),
  );
}

/** Let the page's pending promises resolve. */
async function flush(): Promise<void> {
  for (let index = 0; index < 5; index += 1) {
    await Promise.resolve();
    await new Promise((resolve) => setTimeout(resolve, 0));
  }
}

function fill(values: Record<string, string>): void {
  for (const [field, value] of Object.entries(values)) {
    const input = document.querySelector<HTMLInputElement>(`#field-${field}`);
    if (input !== null) {
      input.value = value;
    }
  }
}

const VALID_VALUES = {
  firstName: 'Ana',
  lastName: 'Novak',
  email: 'ana@example.org',
  organization: 'Univerza v Mariboru',
};

const SUCCESS_BODY = {
  reference: 'REG-20260921-ABCDEFGHJK',
  variant: 'external',
  email: 'ana@example.org',
  createdAt: '2026-09-21T10:00:00.000Z',
  selectedOptions: [{ optionId: 'workshop-ai', group: 'workshops', displayName: 'Workshop: AI' }],
  confirmationEmailQueuedTo: 'ana@example.org',
};

beforeEach(() => {
  document.body.innerHTML = PAGE_HTML;
  document.title = 'External participant registration';
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('page load', () => {
  it('renders the fixed fields supplied by the backend', async () => {
    scriptBackend({ status: 201, body: SUCCESS_BODY });
    initRegistrationPage('external');
    await flush();

    for (const field of CONFIG.fields) {
      expect(document.querySelector(`#field-${field}`)).not.toBeNull();
    }
    expect(document.querySelector('#field-studentId')).toBeNull();
  });

  it('labels every input and marks required ones (AC-G-13)', async () => {
    scriptBackend({ status: 201, body: SUCCESS_BODY });
    initRegistrationPage('external');
    await flush();

    for (const field of CONFIG.fields) {
      const label = document.querySelector(`label[for="field-${field}"]`);
      expect(label?.textContent?.length).toBeGreaterThan(0);
      expect(document.querySelector<HTMLInputElement>(`#field-${field}`)?.required).toBe(true);
    }
  });

  it('renders only option groups that have options (AC-001-02)', async () => {
    scriptBackend({ status: 201, body: SUCCESS_BODY });
    initRegistrationPage('external');
    await flush();

    const legends = Array.from(document.querySelectorAll('legend')).map((node) => node.textContent);
    expect(legends).toEqual(['Workshops', 'Meals']);
    expect(document.querySelector('#option-workshop-ai')).not.toBeNull();
  });

  it('renders the mandatory consent unchecked (AC-G-08)', async () => {
    scriptBackend({ status: 201, body: SUCCESS_BODY });
    initRegistrationPage('external');
    await flush();

    const consent = document.querySelector<HTMLInputElement>('#consent-privacy');
    expect(consent).not.toBeNull();
    expect(consent?.checked).toBe(false);
    expect(consent?.required).toBe(true);
  });

  it('shows a technical error and no form when the configuration cannot be loaded', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503, json: () => Promise.resolve({}) }));
    initRegistrationPage('external');
    await flush();

    expect(document.querySelector<HTMLFormElement>('#registration-form')?.hidden).toBe(true);
    expect(document.querySelector('#form-summary')?.textContent).toContain('could not be loaded');
  });
});

describe('client-side validation', () => {
  it('does not send anything when required fields are empty', async () => {
    scriptBackend({ status: 201, body: SUCCESS_BODY });
    initRegistrationPage('external');
    await flush();

    document.querySelector<HTMLFormElement>('#registration-form')?.dispatchEvent(new Event('submit'));
    await flush();

    const fetchMock = globalThis.fetch as unknown as ReturnType<typeof vi.fn>;
    const submitCalls = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/registrations'));
    expect(submitCalls).toHaveLength(0);
    expect(document.querySelector('#confirmation')?.hasAttribute('hidden')).toBe(true);
  });

  it('marks the offending inputs as invalid', async () => {
    scriptBackend({ status: 201, body: SUCCESS_BODY });
    initRegistrationPage('external');
    await flush();

    fill({ ...VALID_VALUES, email: 'not-an-email' });
    document.querySelector<HTMLInputElement>('#consent-privacy')!.checked = true;
    document.querySelector<HTMLFormElement>('#registration-form')?.dispatchEvent(new Event('submit'));
    await flush();

    const email = document.querySelector<HTMLInputElement>('#field-email');
    expect(email?.getAttribute('aria-invalid')).toBe('true');
    expect(document.querySelector('#field-email-error')?.textContent).toContain('valid email');
  });
});

describe('submission outcomes', () => {
  async function submitValidForm(response: ScriptedResponse | Error): Promise<void> {
    scriptBackend(response);
    initRegistrationPage('external');
    await flush();
    fill(VALID_VALUES);
    document.querySelector<HTMLInputElement>('#consent-privacy')!.checked = true;
    document.querySelector<HTMLInputElement>('#option-workshop-ai')!.checked = true;
    document.querySelector<HTMLFormElement>('#registration-form')?.dispatchEvent(new Event('submit'));
    await flush();
  }

  it('shows the confirmation only after a 201, with the reference and the email address (AC-004-01..03)', async () => {
    await submitValidForm({ status: 201, body: SUCCESS_BODY });

    const confirmation = document.querySelector('#confirmation');
    expect(confirmation?.hasAttribute('hidden')).toBe(false);
    expect(confirmation?.textContent).toContain('has been received');
    expect(confirmation?.textContent).toContain('REG-20260921-ABCDEFGHJK');
    expect(confirmation?.textContent).toContain('ana@example.org');
    expect(confirmation?.textContent).toContain('Workshop: AI');
    expect(document.querySelector<HTMLFormElement>('#registration-form')?.hidden).toBe(true);
  });

  it('shows backend field errors and no confirmation on a validation rejection (AC-004-04)', async () => {
    await submitValidForm({
      status: 400,
      body: {
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Some fields are invalid.',
          fields: [{ field: 'organization', code: 'required', message: 'Organization / institution is required.' }],
        },
      },
    });

    expect(document.querySelector('#confirmation')?.hasAttribute('hidden')).toBe(true);
    expect(document.querySelector('#field-organization-error')?.textContent).toContain('is required');
    expect(document.querySelector<HTMLInputElement>('#field-organization')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('shows option errors in the summary, since they belong to no single input', async () => {
    await submitValidForm({
      status: 400,
      body: {
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Some fields are invalid.',
          fields: [{ field: 'selectedOptionIds', code: 'unknown_option', message: 'Unknown conference option: "x".' }],
        },
      },
    });

    expect(document.querySelector('#form-summary')?.textContent).toContain('Unknown conference option');
    expect(document.querySelector('#confirmation')?.hasAttribute('hidden')).toBe(true);
  });

  it('distinguishes a technical failure from a validation failure and keeps the entered data (AC-004-05)', async () => {
    await submitValidForm(new TypeError('Failed to fetch'));

    const summary = document.querySelector('#form-summary');
    expect(summary?.hasAttribute('hidden')).toBe(false);
    expect(summary?.textContent).toContain('could not be sent');
    expect(document.querySelector('#confirmation')?.hasAttribute('hidden')).toBe(true);
    // The form is still there with the participant's data.
    expect(document.querySelector<HTMLFormElement>('#registration-form')?.hidden).toBe(false);
    expect(document.querySelector<HTMLInputElement>('#field-firstName')?.value).toBe('Ana');
  });

  it('re-enables the submit button after a failure so the participant can retry (AC-004-05, AC-004-06)', async () => {
    await submitValidForm({ status: 500, body: { error: { code: 'INTERNAL_ERROR', message: 'x' } } });
    expect(document.querySelector<HTMLButtonElement>('#submit-button')?.disabled).toBe(false);
  });

  it('shows a rate-limit message without a confirmation', async () => {
    await submitValidForm({ status: 429, body: { error: { code: 'RATE_LIMITED', message: 'Too many attempts.' } } });
    expect(document.querySelector('#form-summary')?.textContent).toContain('Too many attempts');
    expect(document.querySelector('#confirmation')?.hasAttribute('hidden')).toBe(true);
  });

  it('sends the honeypot value and the form token with the submission', async () => {
    await submitValidForm({ status: 201, body: SUCCESS_BODY });

    const fetchMock = globalThis.fetch as unknown as ReturnType<typeof vi.fn>;
    const submitCall = fetchMock.mock.calls.find((call) => String(call[0]).includes('/registrations'));
    const body = JSON.parse((submitCall?.[1] as RequestInit).body as string) as Record<string, unknown>;
    expect(body.formToken).toBe('test-token');
    expect(body.website).toBe('');
    expect(body.selectedOptionIds).toEqual(['workshop-ai']);
    expect(body.consents).toEqual({ privacy: true });
  });

  it('trims values before sending them, matching the backend rule (AC-001-11)', async () => {
    scriptBackend({ status: 201, body: SUCCESS_BODY });
    initRegistrationPage('external');
    await flush();
    fill({ ...VALID_VALUES, firstName: '  Ana  ' });
    document.querySelector<HTMLInputElement>('#consent-privacy')!.checked = true;
    document.querySelector<HTMLFormElement>('#registration-form')?.dispatchEvent(new Event('submit'));
    await flush();

    const fetchMock = globalThis.fetch as unknown as ReturnType<typeof vi.fn>;
    const submitCall = fetchMock.mock.calls.find((call) => String(call[0]).includes('/registrations'));
    const body = JSON.parse((submitCall?.[1] as RequestInit).body as string) as Record<string, unknown>;
    expect(body.firstName).toBe('Ana');
  });
});

describe('rendering safety (AC-G-09)', () => {
  it('renders a hostile reference as text, not as markup', async () => {
    scriptBackend({
      status: 201,
      body: { ...SUCCESS_BODY, reference: '<img src=x onerror=alert(1)>' },
    });
    initRegistrationPage('external');
    await flush();
    fill(VALID_VALUES);
    document.querySelector<HTMLInputElement>('#consent-privacy')!.checked = true;
    document.querySelector<HTMLFormElement>('#registration-form')?.dispatchEvent(new Event('submit'));
    await flush();

    const confirmation = document.querySelector('#confirmation');
    expect(confirmation?.querySelector('img')).toBeNull();
    expect(confirmation?.textContent).toContain('<img src=x onerror=alert(1)>');
  });
});
