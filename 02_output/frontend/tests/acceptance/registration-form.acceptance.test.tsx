import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from '../../src/App';

type Call = { url: string; method: string; body?: unknown };

const CONFIG = { conferenceName: 'Konferenca 2026', captchaTestMode: true, recaptchaSiteKey: '' };
const CONSENTS = [
  {
    id: 'data-processing',
    text: 'I agree that the organizers process my personal data.',
    mandatory: true,
  },
  { id: 'photos', text: 'I agree to appear in conference photos.', mandatory: false },
];
const OPTIONS: Record<string, unknown> = {
  EXTERNAL: {
    type: 'EXTERNAL',
    options: [
      { id: 'ws-secure-web', name: 'Workshop: Secure web development', category: 'WORKSHOP' },
      { id: 'ev-welcome', name: 'Welcome reception', category: 'EVENT' },
      { id: 'ev-gala-dinner', name: 'Gala dinner', category: 'EVENT' },
      { id: 'meal-lunch-day1', name: 'Lunch, day 1', category: 'MEAL' },
      { id: 'other-city-tour', name: 'Ljubljana city tour', category: 'OTHER' },
    ],
    consents: CONSENTS,
    categoryLimits: {},
  },
  STUDENT: {
    type: 'STUDENT',
    options: [
      { id: 'ws-secure-web', name: 'Workshop: Secure web development', category: 'WORKSHOP' },
      { id: 'ev-career-fair', name: 'Student career fair', category: 'EVENT' },
      { id: 'meal-lunch-day1', name: 'Lunch, day 1', category: 'MEAL' },
    ],
    consents: CONSENTS,
    categoryLimits: {},
  },
};
const REFERENCE = '0b8f7a4e-2f53-4f0a-9d1e-6f1c2b3a4d5e';

let calls: Call[];
let registrationResponse: { status: number; body: unknown };

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  calls = [];
  registrationResponse = { status: 201, body: {} };
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      const method = (init?.method ?? 'GET').toUpperCase();
      const body = init?.body ? JSON.parse(String(init.body)) : undefined;
      calls.push({ url, method, body });
      if (url.startsWith('/api/config')) return json(200, CONFIG);
      const type = /\/api\/options\?type=(\w+)/.exec(url)?.[1];
      if (type) return json(200, OPTIONS[type]);
      if (url.startsWith('/api/registrations') && method === 'POST') {
        return json(registrationResponse.status, registrationResponse.body);
      }
      return json(404, { message: 'Not found', errors: [] });
    }),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

const posts = () => calls.filter((c) => c.method === 'POST');

async function openForm(type: 'External participant' | 'Student' = 'External participant') {
  render(<App />);
  fireEvent.click(await screen.findByRole('radio', { name: type }));
  await screen.findByRole('checkbox', { name: 'Workshop: Secure web development' });
}

function field(label: RegExp): HTMLInputElement {
  return screen.getByLabelText(label) as HTMLInputElement;
}

function type(label: RegExp, value: string) {
  fireEvent.change(field(label), { target: { value } });
}

function fillValidExternal() {
  type(/^First name/, 'Špela');
  type(/^Last name/, 'Čučnik-Žagar');
  type(/^Email/, 'spela@example.si');
  type(/^Organization/, 'Zavod Škofja Loka');
  fireEvent.click(screen.getByRole('checkbox', { name: 'Workshop: Secure web development' }));
  fireEvent.click(screen.getByRole('checkbox', { name: 'Lunch, day 1' }));
  fireEvent.click(screen.getByRole('checkbox', { name: /^I agree that the organizers/ }));
  fireEvent.click(screen.getByRole('checkbox', { name: /I am not a robot \(test mode\)/ }));
}

describe('registration form (frozen acceptance tests)', () => {
  it('AC-001-08 every consent checkbox starts unchecked', async () => {
    await openForm();

    expect(
      screen.getByRole('checkbox', { name: /^I agree that the organizers/ }),
    ).not.toBeChecked();
    expect(screen.getByRole('checkbox', { name: /^I agree to appear/ })).not.toBeChecked();
  });

  it('AC-002-03 the fields follow the chosen registration type', async () => {
    await openForm();
    expect(field(/^Organization/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Student ID/)).toBeNull();

    fireEvent.click(screen.getByRole('radio', { name: 'Student' }));

    expect(await screen.findByLabelText(/^Study institution/)).toBeInTheDocument();
    expect(field(/^Study programme/)).toBeInTheDocument();
    expect(field(/^Student ID/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Organization/)).toBeNull();

    fireEvent.click(screen.getByRole('radio', { name: 'External participant' }));

    expect(await screen.findByLabelText(/^Organization/)).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Study institution/)).toBeNull();
  });

  it('AC-003-03 active options are shown grouped by category', async () => {
    await openForm();

    const groups: Record<string, string> = {
      Workshops: 'Workshop: Secure web development',
      Events: 'Welcome reception',
      Meals: 'Lunch, day 1',
      'Other activities': 'Ljubljana city tour',
    };
    for (const [group, option] of Object.entries(groups)) {
      const fieldset = screen.getByRole('group', { name: group });
      expect(within(fieldset).getByRole('checkbox', { name: option })).toBeInTheDocument();
    }
  });

  it('AC-003-04 the options offered follow the registration type', async () => {
    await openForm();
    expect(screen.getByRole('checkbox', { name: 'Gala dinner' })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('radio', { name: 'Student' }));

    expect(
      await screen.findByRole('checkbox', { name: 'Student career fair' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('checkbox', { name: 'Gala dinner' })).toBeNull();
    expect(calls.some((c) => c.url === '/api/options?type=STUDENT')).toBe(true);
  });

  it('AC-004-01 an accepted registration shows the confirmation', async () => {
    registrationResponse = {
      status: 201,
      body: {
        reference: REFERENCE,
        type: 'EXTERNAL',
        firstName: 'Špela',
        lastName: 'Čučnik-Žagar',
        email: 'spela@example.si',
        options: [
          { id: 'ws-secure-web', name: 'Workshop: Secure web development', category: 'WORKSHOP' },
          { id: 'meal-lunch-day1', name: 'Lunch, day 1', category: 'MEAL' },
        ],
        submittedAt: '2026-09-30T10:00:00Z',
      },
    };
    await openForm();
    fillValidExternal();

    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    const heading = await screen.findByRole('heading', { name: /Registration received/ });
    expect(heading).toBeInTheDocument();
    expect(screen.getByText(REFERENCE, { exact: false })).toBeInTheDocument();
    expect(screen.getByText(/Workshop: Secure web development/)).toBeInTheDocument();
    expect(screen.getByText(/Lunch, day 1/)).toBeInTheDocument();
    expect(posts()).toHaveLength(1);
    expect(posts()[0].url).toBe('/api/registrations');
    expect(posts()[0].body).toEqual({
      type: 'EXTERNAL',
      firstName: 'Špela',
      lastName: 'Čučnik-Žagar',
      email: 'spela@example.si',
      organization: 'Zavod Škofja Loka',
      optionIds: ['ws-secure-web', 'meal-lunch-day1'],
      consentIds: ['data-processing'],
      captchaToken: 'test-mode-token',
    });
  });

  it('AC-004-02 backend field errors are shown next to the fields and no confirmation', async () => {
    registrationResponse = {
      status: 400,
      body: {
        message: 'Please correct the marked fields.',
        errors: [
          { field: 'email', message: 'This email address is not accepted.' },
          { field: 'organization', message: 'Organization is required.' },
        ],
      },
    };
    await openForm();
    fillValidExternal();

    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() =>
      expect(field(/^Email/)).toHaveAccessibleDescription(/This email address is not accepted\./),
    );
    expect(field(/^Email/)).toHaveAttribute('aria-invalid', 'true');
    expect(field(/^Organization/)).toHaveAccessibleDescription(/Organization is required\./);
    expect(screen.queryByRole('heading', { name: /Registration received/ })).toBeNull();
  });

  it('AC-004-02 a server error shows no confirmation', async () => {
    registrationResponse = {
      status: 500,
      body: { message: 'Registration could not be saved. Please try again.', errors: [] },
    };
    await openForm();
    fillValidExternal();

    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be saved/);
    expect(screen.queryByRole('heading', { name: /Registration received/ })).toBeNull();
  });

  it('AC-004-03 empty required fields are reported before any request is sent', async () => {
    await openForm();
    fillValidExternal();
    type(/^First name/, '   ');

    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() => expect(field(/^First name/)).toHaveAttribute('aria-invalid', 'true'));
    expect(field(/^First name/).getAttribute('aria-describedby')).toBeTruthy();
    expect(field(/^First name/)).toHaveAccessibleDescription(/.+/);
    expect(posts()).toHaveLength(0);
  });

  it('AC-004-03 an invalid email is reported before any request is sent', async () => {
    await openForm();
    fillValidExternal();
    type(/^Email/, 'spela@');

    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() => expect(field(/^Email/)).toHaveAttribute('aria-invalid', 'true'));
    expect(field(/^Email/)).toHaveAccessibleDescription(/.+/);
    expect(posts()).toHaveLength(0);
  });

  it('AC-004-03 a missing mandatory consent is reported before any request is sent', async () => {
    await openForm();
    fillValidExternal();
    fireEvent.click(screen.getByRole('checkbox', { name: /^I agree that the organizers/ }));

    fireEvent.click(screen.getByRole('button', { name: 'Register' }));

    await waitFor(() =>
      expect(
        screen.getByRole('checkbox', { name: /^I agree that the organizers/ }),
      ).toHaveAttribute('aria-invalid', 'true'),
    );
    expect(posts()).toHaveLength(0);
  });
});
